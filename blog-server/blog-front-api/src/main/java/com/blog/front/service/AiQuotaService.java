package com.blog.front.service;

import com.blog.common.entity.AiSetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 调用配额（全站预算 + 三层窗口 + 并发锁 + IP 兜底）。
 *
 * 为什么放在 Java 而不是 RAG：RAG 侧看不到 JWT，不知道谁是管理员，
 * 放那边就没法实现「管理员无限」。
 *
 * 计数全部走 Redis，不查 MySQL —— 限流要求毫秒级判断。
 * 表（ai_usage_daily）只负责事后分析；唯一例外是全站预算（P4），
 * 它必须读那张表的 SUM，因为金额只在写库时算过（见 {@link AiBudgetService}）。
 *
 * 故障策略：Redis / 数据库不可用时 **fail-open**（放行 + WARN）。
 * 限流是防护手段，不是核心功能；宁可漏拦，也不能让所有人用不了 AI。
 * 真正防「key 被盗刷」的是全站日预算这一层，它挡的是钱。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuotaService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 今日被拦记录：{@code ai:hit:{yyyyMMdd}} 是 Hash（field=userId, value=次数），
     * {@code ai:hit:last:{yyyyMMdd}} 是另一个 Hash（field=userId, value=「时间|触发层」）。
     *
     * <p>为什么不用 MySQL：被拦的请求本来就没落库（拦在转发之前），为了统计去写一条
     * 业务表等于给拒绝路径加一次写库；而这张表只需要看"今天"，Redis 两天过期正合适。
     */
    private static final String HIT_PREFIX = "ai:hit:";
    private static final String HIT_LAST_PREFIX = "ai:hit:last:";

    private final StringRedisTemplate redis;

    /** 阈值以数据库 ai_setting 为准（后台可改，5 秒内生效）；库读不到才回落到 application.yml */
    private final AiModelService modelService;

    /** 全站日预算（P4）：唯一一个挡"整体花太多钱"的护栏 */
    private final AiBudgetService budgetService;

    @Value("${ai.limits.enabled:true}")
    private boolean enabled;

    @Value("${ai.limits.minute:5}")
    private int minuteLimit;

    @Value("${ai.limits.hourly:30}")
    private int hourlyLimit;

    @Value("${ai.limits.daily:100}")
    private int dailyLimit;

    @Value("${ai.limits.ip-daily:300}")
    private int ipDailyLimit;

    /**
     * 判定结果
     *
     * @param scope   触发的层：minute / hourly / daily / ip / concurrent；放行时为 null
     * @param retryAfterSeconds 还要等多久（秒）
     */
    public record Decision(boolean allowed, String scope, long retryAfterSeconds, int used, int limit) {

        static Decision allow() {
            return new Decision(true, null, 0, 0, 0);
        }

        public String message() {
            if (allowed) {
                return "";
            }
            long minutes = Math.max(1, (retryAfterSeconds + 59) / 60);
            return switch (scope) {
                case "minute" -> "问得有点快，休息 " + retryAfterSeconds + " 秒再问我吧。";
                case "hourly" -> "这一小时聊得有点多，约 " + minutes + " 分钟后再来。";
                case "daily" -> "今天的 AI 额度用完了，明天 0 点重置。";
                // 与 daily 必须能分清：daily 是"你自己用超了"，budget 是"服务整体停了"。
                // 不暴露预算金额，那是运营数据。
                case "budget" -> "今天的 AI 服务总额度已用完，明天 0 点恢复。";
                case "ip" -> "当前网络今天的 AI 额度已用完，换网络或明天再来。";
                case "concurrent" -> "上一个问题还在回答中，等它结束再问。";
                default -> "AI 额度暂时不可用，请稍后再试。";
            };
        }
    }

    /**
     * 检查并占用一次额度。放行时会同时占住并发锁，调用方**必须在 finally 里 release()**。
     *
     * @param userId    当前用户（未登录为 null，正常流程走不到这里，Security 已拦）
     * @param unlimited 管理员为 true，直接放行且不计数（但仍会写用量表）
     */
    public Decision check(Long userId, boolean unlimited, String ip) {
        if (!enabled || unlimited || userId == null) {
            return Decision.allow();
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        int[] lim = effectiveLimits();

        // 1) 并发锁：同一用户同时只允许 1 个进行中的请求，否则开 10 个标签页就能绕过所有计数
        String inflightKey = "ai:inflight:" + userId;
        if (!acquireInflight(inflightKey)) {
            return new Decision(false, "concurrent", 15, 1, 1);
        }

        // 2) 全站日预算（P4）。必须排在个人窗口**之前**：
        //    预算已尽时个人额度不该再被扣，否则用户的次数白烧了、明天照样能用 —— 纯亏。
        //    管理员（unlimited）在上面的 if 里已经放行，这是有意的：
        //    预算耗尽时运维仍要能进后台测试模型连通性，代价是管理员用量也算进预算。
        AiBudgetService.Status budget = budgetService.status();
        if (budget.exceeded()) {
            release(userId);
            return new Decision(false, "budget", secondsToMidnight(now), 0, 0);
        }

        // 3) 三层窗口，由细到粗
        Decision d = bump("ai:rl:m:" + userId + ":" + now.format(MINUTE_FMT),
                lim[0], "minute", 60 - now.getSecond());
        if (!d.allowed()) {
            release(userId);
            return d;
        }

        d = bump("ai:rl:h:" + userId + ":" + now.format(HOUR_FMT),
                lim[1], "hourly", (60 - now.getMinute()) * 60 - now.getSecond());
        if (!d.allowed()) {
            release(userId);
            return d;
        }

        d = bump("ai:rl:d:" + userId + ":" + now.format(DAY_FMT),
                lim[2], "daily", secondsToMidnight(now));
        if (!d.allowed()) {
            release(userId);
            return d;
        }

        // 4) IP 兜底：只按用户算的话，换个 OAuth 小号就重置了
        //    阈值故意放宽（默认 300/天），避免运营商 NAT / 公司出口误伤整个办公室
        if (ip != null && !ip.isBlank()) {
            d = bump("ai:rl:ip:" + Integer.toHexString(ip.hashCode()) + ":" + now.format(DAY_FMT),
                    ipDailyLimit, "ip", secondsToMidnight(now));
            if (!d.allowed()) {
                release(userId);
                return d;
            }
        }

        return Decision.allow();
    }

    /** 释放并发锁。请求结束（含异常、中止）必须调用，否则用户会被锁 5 分钟。 */
    public void release(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.delete("ai:inflight:" + userId);
        } catch (Exception e) {
            log.warn("释放 AI 并发锁失败（{}s 后自动过期）: {}", 300, e.getMessage());
        }
    }

    // ---------------- 被拦记录（后台「今日限流」表的数据源） ----------------

    /**
     * 记一次"被拦"。任何异常都吞掉 —— 统计用的旁路数据，不能影响拒绝响应本身。
     *
     * <p>只记登录用户：未登录在 Security 层就被挡了，走不到这里；
     * IP 维度的拦截也归到具体用户名下，因为那是"这个人用的网络被拦了"。
     */
    public void recordBlocked(Long userId, String scope) {
        if (userId == null) {
            return;
        }
        try {
            String day = LocalDateTime.now(ZONE).format(DAY_FMT);
            String countKey = HIT_PREFIX + day;
            String lastKey = HIT_LAST_PREFIX + day;
            redis.opsForHash().increment(countKey, String.valueOf(userId), 1L);
            redis.expire(countKey, Duration.ofHours(48));
            redis.opsForHash().put(lastKey, String.valueOf(userId),
                    LocalDateTime.now(ZONE).format(TIME_FMT) + "|" + (scope == null ? "" : scope));
            redis.expire(lastKey, Duration.ofHours(48));
        } catch (Exception e) {
            log.debug("记录 AI 限流命中失败（不影响拒绝响应）: {}", e.getMessage());
        }
    }

    /**
     * 今日被拦用户，按次数倒序。
     *
     * <p>Redis 不可用返回空列表 —— 报表空着比抛 500 好，管理员至少能看到其他几张卡。
     */
    public List<Hit> todayHits(int limit) {
        int n = Math.max(1, Math.min(100, limit));
        try {
            String day = LocalDateTime.now(ZONE).format(DAY_FMT);
            Map<Object, Object> counts = redis.opsForHash().entries(HIT_PREFIX + day);
            if (counts.isEmpty()) {
                return List.of();
            }
            Map<Object, Object> lasts = redis.opsForHash().entries(HIT_LAST_PREFIX + day);

            List<Hit> hits = new ArrayList<>(counts.size());
            for (Map.Entry<Object, Object> e : counts.entrySet()) {
                Long userId = parseLong(e.getKey());
                if (userId == null) {
                    continue;
                }
                String raw = lasts.get(e.getKey()) == null ? "" : String.valueOf(lasts.get(e.getKey()));
                int sep = raw.indexOf('|');
                String lastAt = sep < 0 ? raw : raw.substring(0, sep);
                String scope = sep < 0 ? "" : raw.substring(sep + 1);
                Long count = parseLong(e.getValue());
                hits.add(new Hit(userId, count == null ? 0 : count.intValue(), scope, lastAt));
            }
            hits.sort((a, b) -> Integer.compare(b.count(), a.count()));
            return hits.size() > n ? hits.subList(0, n) : hits;
        } catch (Exception e) {
            log.warn("读取 AI 限流命中记录失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 被拦记录
     *
     * @param count  今日被拦次数
     * @param scope  最后一次触发的层（minute/hourly/daily/ip/concurrent）
     * @param lastAt 最后一次触发时间（yyyy-MM-dd HH:mm:ss）
     */
    public record Hit(Long userId, int count, String scope, String lastAt) {
    }

    /** 今日剩余次数（给前端展示"还能问几次"）。取最紧的那一层。 */
    public int remainingToday(Long userId, boolean unlimited) {
        if (!enabled || unlimited || userId == null) {
            return Integer.MAX_VALUE;
        }
        try {
            String dayKey = "ai:rl:d:" + userId + ":" + LocalDateTime.now(ZONE).format(DAY_FMT);
            String used = redis.opsForValue().get(dayKey);
            return Math.max(0, effectiveLimits()[2] - (used == null ? 0 : Integer.parseInt(used)));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    public int dailyLimit() {
        return effectiveLimits()[2];
    }

    /**
     * 取生效阈值：数据库优先，读不到（表还没建、库挂了）就用配置文件的值。
     * 任一为 0 表示该层不限制。
     */
    private int[] effectiveLimits() {
        int minute = minuteLimit;
        int hourly = hourlyLimit;
        int daily = dailyLimit;
        try {
            AiSetting s = modelService == null ? null : modelService.settingCached();
            if (s != null) {
                if (s.getMinuteLimit() != null) {
                    minute = s.getMinuteLimit();
                }
                if (s.getHourlyLimit() != null) {
                    hourly = s.getHourlyLimit();
                }
                if (s.getDailyLimit() != null) {
                    daily = s.getDailyLimit();
                }
            }
        } catch (Exception e) {
            log.warn("读取 AI 限流配置失败，使用配置文件默认值: {}", e.getMessage());
        }
        return new int[]{minute, hourly, daily};
    }

    // ---------------- 内部 ----------------

    private boolean acquireInflight(String key) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(5));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("AI 并发锁不可用，放行: {}", e.getMessage());
            return true;
        }
    }

    private Decision bump(String key, int limit, String scope, long retryAfterSeconds) {
        if (limit <= 0) {
            return Decision.allow();   // 0 / 负数 = 该层不限制
        }
        try {
            Long v = redis.opsForValue().increment(key);
            if (v != null && v == 1L) {
                redis.expire(key, ttlOf(scope));
            } else if (v != null) {
                ensureTtl(key, scope);
            }
            int used = v == null ? 0 : v.intValue();
            if (used > limit) {
                return new Decision(false, scope, Math.max(1, retryAfterSeconds), used, limit);
            }
            return Decision.allow();
        } catch (Exception e) {
            log.warn("AI 限流计数失败(fail-open, {}): {}", scope, e.getMessage());
            return Decision.allow();
        }
    }

    /**
     * TTL 自愈：只在确认「这个 key 没有过期时间」时补设一次。
     *
     * <p>为什么必须有这一步：原来只在 {@code v == 1} 时设过期时间。如果那一次
     * INCR 成功、随后的 EXPIRE 失败（Redis 主从切换、瞬时网络错误、被 FLUSH 后重建），
     * 这个计数 key 就**永远没有 TTL** —— 计数只增不减，用户从那一刻起每次提问都被拒，
     * 而且「等一分钟就好」并不成立，只有人工删 key 才能解开。这是会持续到被发现的
     * 线上事故，代价只是每次自增多一次 TTL 查询（单机 Redis 亚毫秒级）。
     *
     * <p>返回 {@code null} 时不动：部分代理 / pipeline 场景不支持 TTL 查询，
     * 那种情况下宁可维持现状，也不要每个请求都硬发一次 EXPIRE（会不断把窗口往后滚）。
     */
    private void ensureTtl(String key, String scope) {
        try {
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            if (ttl != null && ttl == -1L) {
                redis.expire(key, ttlOf(scope));
                log.warn("AI 限流计数 {} 缺少 TTL（多半是上次 INCR 成功但 EXPIRE 失败），已补设 {}",
                        key, ttlOf(scope));
            }
        } catch (Exception e) {
            log.debug("查询限流 key 的 TTL 失败（忽略，不影响本次判定）: {}", e.getMessage());
        }
    }

    private Duration ttlOf(String scope) {
        return switch (scope) {
            case "minute" -> Duration.ofMinutes(2);
            case "hourly" -> Duration.ofHours(2);
            default -> Duration.ofHours(26);
        };
    }

    private long secondsToMidnight(LocalDateTime now) {
        LocalDateTime next = now.toLocalDate().plusDays(1).atStartOfDay();
        return Math.max(1, ChronoUnit.SECONDS.between(now, next));
    }

    /** Redis 里的值都是字符串，脏数据（手工写坏 / 序列化前缀）返回 null 让调用方跳过 */
    private Long parseLong(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
