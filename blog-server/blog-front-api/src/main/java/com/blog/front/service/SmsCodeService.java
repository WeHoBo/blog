package com.blog.front.service;

import cn.hutool.core.util.RandomUtil;
import com.blog.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码的存储、校验与限流。
 *
 * <p><b>四道防线，缺一不可</b>。短信是按条计费的真金白银（0.06 元/条），
 * 没有限流的接口等于给陌生人开了一张可以随便刷的支票：
 * <ol>
 *   <li>同号冷却 60 秒 —— 防连点、防脚本循环</li>
 *   <li>同号每天 5 条 —— 哪怕换 IP 也刷不动同一个受害者</li>
 *   <li>同 IP 每天 10 条 —— 防一台机器批量换号刷</li>
 *   <li>全局每天 200 条 —— 兜底熔断，被刷时把损失锁在 12 元以内</li>
 * </ol>
 * 校验侧再加一条：同一验证码连续错 5 次立刻作废，防 6 位数字穷举。
 *
 * <p><b>Redis 故障时 fail-open</b>：读不到限流计数就当作没超限。
 * 宁可多放一次请求，也不能让缓存抖动把正常用户挡在登录页外面 ——
 * 这与本项目 {@code ApplyService} 的取舍保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    /** 验证码用途。同一手机号在三种场景下的验证码互不通用 */
    public enum Scene {
        REGISTER("注册"),
        LOGIN("登录"),
        RESET("重置密码"),
        BIND("绑定手机");

        private final String label;

        Scene(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 同号冷却：1 条/分钟 */
    private static final int COOLDOWN_SECONDS = 60;
    /** 同号每日上限 */
    private static final int DAILY_PER_PHONE = 5;
    /** 同 IP 每日上限 */
    private static final int DAILY_PER_IP = 10;
    /** 全局每日上限（熔断） */
    private static final int DAILY_GLOBAL = 200;
    /** 同一个验证码允许校验失败的次数 */
    private static final int MAX_VERIFY_FAIL = 5;

    private final StringRedisTemplate redis;
    private final SmsService smsService;

    // ---------------- 对外 ----------------

    /**
     * 生成并发送验证码。
     *
     * <p>顺序刻意是「先发短信，成功了再写缓存」：
     * 反过来的话，一旦短信下发失败，缓存里却留着一个用户永远收不到的验证码，
     * 他在有效期内怎么输都是"验证码不正确"，且还会被冷却挡住不能重发 —— 体验直接死锁。
     */
    public void send(String phone, Scene scene, String ip) {
        if (!smsService.isEnabled()) {
            throw new BusinessException(500, "短信服务未开启，请联系站长");
        }
        checkCooldown(phone);
        checkPhoneDaily(phone);
        checkIpDaily(ip);
        checkGlobalDaily();

        String code = RandomUtil.randomNumbers(6);
        smsService.sendCode(phone, code);

        // 短信发出去了才落库。上面任何一步抛异常都不会留下脏数据。
        redis.opsForValue().set(codeKey(scene, phone), code, validDuration());
        // 失败计数与验证码同生命周期：重新获取验证码 = 重新开始 5 次机会
        redis.delete(failKey(scene, phone));

        markSent(phone, ip);
        // 只记打码号码与场景，不记验证码本身
        log.info("验证码已发送 phone={} scene={} ip={}", SmsService.mask(phone), scene.name(), maskIp(ip));
    }

    /**
     * 校验验证码。成功即销毁（一次性），失败累加次数，超过 5 次作废。
     */
    public void verify(String phone, Scene scene, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new BusinessException(400, "验证码格式不正确");
        }
        String key = codeKey(scene, phone);
        String expected = redis.opsForValue().get(key);
        if (expected == null) {
            throw new BusinessException(400, "验证码已过期，请重新获取");
        }
        if (expected.equals(code)) {
            redis.delete(key);
            redis.delete(failKey(scene, phone));
            return;
        }

        // 失败计数：达到上限就把验证码删掉，逼迫重新获取（同时也是对穷举的封堵）
        int fails = bumpFail(scene, phone);
        if (fails >= MAX_VERIFY_FAIL) {
            redis.delete(key);
            redis.delete(failKey(scene, phone));
            throw new BusinessException(400, "验证码错误次数过多，请重新获取");
        }
        throw new BusinessException(400, "验证码不正确，还可以再试 " + (MAX_VERIFY_FAIL - fails) + " 次");
    }

    // ---------------- 限流细节 ----------------

    private void checkCooldown(String phone) {
        String key = cooldownKey(phone);
        Long ttl = safeTtl(key);
        if (ttl != null && ttl > 0) {
            throw new BusinessException(429, "请 " + ttl + " 秒后再获取验证码");
        }
    }

    private void checkPhoneDaily(String phone) {
        int used = readInt(dailyPhoneKey(phone));
        if (used >= DAILY_PER_PHONE) {
            throw new BusinessException(429, "该手机号今日获取验证码次数已达上限，请明天再试");
        }
    }

    private void checkIpDaily(String ip) {
        int used = readInt(dailyIpKey(ip));
        if (used >= DAILY_PER_IP) {
            throw new BusinessException(429, "当前网络今日获取验证码次数过多，请明天再试");
        }
    }

    private void checkGlobalDaily() {
        int used = readInt(globalKey());
        if (used >= DAILY_GLOBAL) {
            // 全局熔断说明大概率在被刷，必须留一条显眼的告警
            log.error("⚠️ 短信全局日上限已达 {} 条，已熔断。请检查是否被恶意刷取，"
                    + "并到阿里云控制台查看发送记录", DAILY_GLOBAL);
            throw new BusinessException(429, "短信通道今日已达上限，请明天再试或联系站长");
        }
    }

    /** 发送成功后的计数与冷却标记。两步都失败也不影响用户已经收到的验证码 */
    private void markSent(String phone, String ip) {
        try {
            redis.opsForValue().set(cooldownKey(phone), "1", Duration.ofSeconds(COOLDOWN_SECONDS));
            bumpDaily(dailyPhoneKey(phone));
            bumpDaily(dailyIpKey(ip));
            bumpDaily(globalKey());
        } catch (Exception e) {
            log.warn("短信计数写入失败（不影响本次已发送的验证码）: {}", e.getMessage());
        }
    }

    private void bumpDaily(String key) {
        Long v = redis.opsForValue().increment(key);
        if (v != null && v == 1L) {
            redis.expire(key, untilTomorrow());
        }
    }

    private int bumpFail(Scene scene, String phone) {
        try {
            String key = failKey(scene, phone);
            Long v = redis.opsForValue().increment(key);
            if (v == null) {
                return 1;
            }
            if (v == 1L) {
                redis.expire(key, validDuration());
            }
            return v.intValue();
        } catch (Exception e) {
            log.warn("验证码失败计数写入失败（忽略）: {}", e.getMessage());
            return 1;
        }
    }

    /** Redis 抖动时视为未超限（fail-open），见类注释 */
    private int readInt(String key) {
        try {
            String raw = redis.opsForValue().get(key);
            return raw == null ? 0 : Integer.parseInt(raw);
        } catch (Exception e) {
            log.warn("读取短信限流计数失败（fail-open）: {}", e.getMessage());
            return 0;
        }
    }

    private Long safeTtl(String key) {
        try {
            return redis.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("读取短信冷却 TTL 失败（fail-open，放行本次）: {}", e.getMessage());
            return null;
        }
    }

    // ---------------- key 拼装 ----------------

    private String codeKey(Scene scene, String phone) {
        return "sms:code:" + scene.name().toLowerCase() + ":" + phone;
    }

    private String failKey(Scene scene, String phone) {
        return "sms:fail:" + scene.name().toLowerCase() + ":" + phone;
    }

    private String cooldownKey(String phone) {
        return "sms:cd:" + phone;
    }

    private String dailyPhoneKey(String phone) {
        return "sms:day:phone:" + phone + ":" + today();
    }

    private String dailyIpKey(String ip) {
        return "sms:day:ip:" + (ip == null ? "unknown" : ip) + ":" + today();
    }

    private String globalKey() {
        return "sms:day:global:" + today();
    }

    private String today() {
        return LocalDateTime.now().toLocalDate().toString();
    }

    private Duration validDuration() {
        return Duration.ofMinutes(Math.max(1, smsService.getValidMinutes()));
    }

    /** 日计数过期时间：到次日零点。至少 1 秒，避免刚好零点时算出 0 导致 key 不过期 */
    private Duration untilTomorrow() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
        long seconds = Math.max(1, Duration.between(now, tomorrow).getSeconds());
        return Duration.ofSeconds(seconds);
    }

    /** IP 打码：保留前三段。IP 属个人信息，不进长期日志 */
    private String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "-";
        }
        int last = ip.lastIndexOf('.');
        return last > 0 ? ip.substring(0, last) + ".*" : "***";
    }
}
