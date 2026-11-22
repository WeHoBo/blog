package com.blog.front.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teaopenapi.models.Config;
import com.blog.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 阿里云「号码认证服务 - 短信认证」封装。
 *
 * <p><b>为什么是 dypnsapi 而不是 dysmsapi</b>：
 * dysmsapi（常规短信服务）要求短信签名完成运营商实名报备，
 * 而个人认证账号的"自用"签名无法报备、"已备案网站"这个签名来源也已下线，
 * 等于个人开发者根本发不出国内短信。dypnsapi 的短信认证免签名申请、免模板报备，
 * 个人实名即可用，代价是只能发验证码、模板与签名由阿里云预置。
 *
 * <p><b>验证码由我们自己生成</b>（而不是用 {@code ##code##} 占位让阿里云生成）：
 * 这样校验逻辑留在服务端，可以区分注册/登录/找回密码三种场景，
 * 也能记录失败次数做锁定。用占位符的话阿里云只负责生成+校验，
 * 同一个手机号的验证码在三种场景间通用，安全性反而更弱。
 */
@Slf4j
@Service
public class SmsService {

    /** 总开关。默认关：没配 AK 的环境（本地/测试）不应因为缺配置而启动失败 */
    @Value("${sms.enabled:false}")
    private boolean enabled;

    @Value("${sms.access-key-id:}")
    private String accessKeyId;

    @Value("${sms.access-key-secret:}")
    private String accessKeySecret;

    /** 阿里云赠送的签名名称（控制台「赠送签名配置」里选定的那个） */
    @Value("${sms.sign-name:}")
    private String signName;

    /** 阿里云赠送的模板 CODE（控制台「赠送模板配置」里选定的那个） */
    @Value("${sms.template-code:}")
    private String templateCode;

    @Value("${sms.endpoint:dypnsapi.aliyuncs.com}")
    private String endpoint;

    /** 验证码有效期（分钟），同时用于 Redis TTL 与短信文案里的"X 分钟内有效" */
    @Value("${sms.valid-minutes:5}")
    private int validMinutes;

    private volatile Client client;

    public boolean isEnabled() {
        return enabled;
    }

    public int getValidMinutes() {
        return validMinutes;
    }

    /**
     * 懒加载客户端。
     *
     * <p>不在 {@code @PostConstruct} 里建：那样没配 AK 的环境会启动即崩，
     * 而后端有一大半功能跟短信无关，不该被一个可选能力拖死。
     */
    private Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        Config config = new Config()
                                .setAccessKeyId(accessKeyId)
                                .setAccessKeySecret(accessKeySecret);
                        config.endpoint = endpoint;
                        config.regionId = "cn-beijing";
                        client = new Client(config);
                    } catch (Exception e) {
                        // 不把 AK 或异常原文带出去：SDK 的异常里可能含请求上下文
                        log.error("短信客户端初始化失败: {}", e.getMessage());
                        throw new BusinessException(500, "短信服务初始化失败，请联系站长");
                    }
                }
            }
        }
        return client;
    }

    /**
     * 发送验证码。失败一律抛 {@link BusinessException}，由全局异常处理器转成
     * HTTP 200 + code=400 给前端 —— 前端据此提示，别让它静默失败。
     *
     * <p>⚠️ 这里**只负责发**，不负责存验证码。存与校验在 {@link SmsCodeService}，
     * 顺序是「先发成功、再写 Redis」，否则发失败却把码写进了缓存，
     * 用户拿到的短信是错的（或者说根本没收到），却在有效期内一直"验证码错误"。
     */
    public void sendCode(String phone, String code) {
        if (!enabled) {
            throw new BusinessException(500, "短信服务未开启，请联系站长");
        }
        if (signName == null || signName.isBlank() || templateCode == null || templateCode.isBlank()) {
            throw new BusinessException(500, "短信签名或模板未配置，请联系站长");
        }

        try {
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    // 传具体验证码（而非 ##code## 占位）⇒ 阿里云只下发、不校验，校验由我们自己来
                    .setTemplateParam("{\"code\":\"" + code + "\",\"min\":\"" + validMinutes + "\"}")
                    .setCodeLength(6L)
                    .setValidTime((long) validMinutes * 60)
                    // 不让阿里云把验证码回显在响应里：它会进日志、进抓包
                    .setReturnVerifyCode(false);

            SendSmsVerifyCodeResponse response = client().sendSmsVerifyCode(request);

            String code_ = response == null || response.getBody() == null
                    ? null : response.getBody().getCode();
            if (!"OK".equalsIgnoreCase(code_)) {
                String message = response == null || response.getBody() == null
                        ? "未知错误" : response.getBody().getMessage();
                // 手机号打码：日志是长期留存物，不存完整号码
                log.warn("短信下发失败 phone={} code={} message={}", mask(phone), code_, message);
                throw new BusinessException(500, "验证码发送失败：" + humanize(message));
            }
            log.info("短信已下发 phone={}", mask(phone));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("短信下发异常 phone={}: {}", mask(phone), e.getMessage());
            throw new BusinessException(500, "验证码发送失败，请稍后重试");
        }
    }

    /** 把阿里云的英文错误翻成人话；未命中规则时给通用提示，不把技术细节甩给用户 */
    private String humanize(String message) {
        if (message == null || message.isBlank()) {
            return "请稍后重试";
        }
        String lower = message.toLowerCase();
        if (lower.contains("isv.mobile_number_illegal")) {
            return "手机号格式不正确";
        }
        if (lower.contains("isv.business_limit_control")) {
            return "该号码发送过于频繁，请稍后再试";
        }
        if (lower.contains("isv.day_limit_control")) {
            return "该号码今日发送次数已达上限";
        }
        if (lower.contains("isv.account_frozen") || lower.contains("isv.out_of_service")) {
            return "短信服务不可用，请联系站长";
        }
        if (lower.contains("signature") || lower.contains("template")) {
            return "短信签名或模板配置有误，请联系站长";
        }
        return "请稍后重试";
    }

    /** 138****8000 —— 日志与报错里一律用打码号码 */
    static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
