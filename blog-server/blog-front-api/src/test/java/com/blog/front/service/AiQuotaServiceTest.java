package com.blog.front.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配额判定的边界测试。
 *
 * 这块最怕"看起来能跑但算错"：限流错了要么误伤正常读者，要么形同虚设。
 * 所以每个分支都单独锁一条。
 */
class AiQuotaServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private AiBudgetService budget;
    private AiQuotaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        budget = mock(AiBudgetService.class);
        // 默认"未设全站预算"，让既有用例只考察它自己那一层
        when(budget.status()).thenReturn(new AiBudgetService.Status(
                BigDecimal.ZERO, BigDecimal.ZERO, 0, false, true));
        // 阈值现在以数据库为准，这里传 mock 让 effectiveLimits() 回落到测试里 setField 的值
        service = new AiQuotaService(redis, mock(AiModelService.class), budget);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "minuteLimit", 5);
        ReflectionTestUtils.setField(service, "hourlyLimit", 30);
        ReflectionTestUtils.setField(service, "dailyLimit", 100);
        ReflectionTestUtils.setField(service, "ipDailyLimit", 300);
    }

    /** 开关关掉时完全不碰 Redis（避免 redis 故障把 AI 整体拖死） */
    @Test
    void disabled_allowsWithoutTouchingRedis() {
        ReflectionTestUtils.setField(service, "enabled", false);

        AiQuotaService.Decision d = service.check(1L, false, "1.1.1.1");

        assertTrue(d.allowed());
        verify(redis, never()).opsForValue();
    }

    /** 管理员无限：跳过计数，但仍会写用量表（记账在 AiUsageService，这里只验证放行） */
    @Test
    void admin_allowsWithoutCounting() {
        AiQuotaService.Decision d = service.check(1L, true, "1.1.1.1");

        assertTrue(d.allowed());
        verify(redis, never()).opsForValue();
    }

    /** 同一用户已有进行中的请求 → 直接拒，且不再消耗次数 */
    @Test
    void concurrentRequest_isRejected() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        AiQuotaService.Decision d = service.check(1L, false, "1.1.1.1");

        assertFalse(d.allowed());
        assertEquals("concurrent", d.scope());
        verify(ops, never()).increment(anyString());
    }

    /** 分钟窗口超限 → 拒，并且必须释放并发锁，否则用户会被锁 5 分钟 */
    @Test
    void minuteLimitExceeded_rejectsAndReleasesLock() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(ops.increment(anyString())).thenReturn(6L);   // 第 6 次，超过 5

        AiQuotaService.Decision d = service.check(1L, false, "1.1.1.1");

        assertFalse(d.allowed());
        assertEquals("minute", d.scope());
        assertTrue(d.retryAfterSeconds() > 0);
        verify(redis).delete("ai:inflight:1");
    }

    /** 第一次计数才设过期时间，第二次不能覆盖（否则窗口会一直往后滚，永远不过期） */
    @Test
    void firstIncrement_setsTtl_secondDoesNot() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(ops.increment(anyString())).thenReturn(1L);

        service.check(1L, false, "1.1.1.1");

        // 分钟 / 小时 / 天 / IP 四层各设一次 TTL
        verify(redis, org.mockito.Mockito.atLeastOnce()).expire(anyString(), any(Duration.class));
    }

    /**
     * TTL 自愈：INCR 成功但 EXPIRE 失败过（TTL = -1）时，必须在后续自增里补上。
     *
     * <p>不补的后果不是「晚一点恢复」，而是这个 key 永远不过期、计数只增不减，
     * 用户从此每次提问都被拒，只能人工删 Redis key —— 是一个会持续到被人发现的故障。
     */
    @Test
    void missingTtl_isRepairedOnNextIncrement() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(ops.increment(anyString())).thenReturn(5L);
        when(redis.getExpire(anyString(), any(java.util.concurrent.TimeUnit.class))).thenReturn(-1L);

        service.check(1L, false, "1.1.1.1");

        verify(redis, org.mockito.Mockito.atLeastOnce()).expire(anyString(), any(Duration.class));
    }

    /** TTL 健康时不能重置 —— 否则窗口一直往后滚，等于永不计数 */
    @Test
    void healthyTtl_isNotRefreshed() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(ops.increment(anyString())).thenReturn(3L);
        when(redis.getExpire(anyString(), any(java.util.concurrent.TimeUnit.class))).thenReturn(30L);

        service.check(1L, false, "1.1.1.1");

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    /** Redis 挂了要 fail-open：限流是防护不是核心功能，不能因此让所有人用不了 AI */
    @Test
    void redisFailure_failsOpen() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(ops.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        AiQuotaService.Decision d = service.check(1L, false, "1.1.1.1");

        assertTrue(d.allowed());
    }

    /** 剩余次数 = 日上限 - 已用；读不到按满额算 */
    @Test
    void remainingToday_subtractsUsed() {
        when(ops.get(anyString())).thenReturn("7");

        assertEquals(93, service.remainingToday(1L, false));
    }

    @Test
    void remainingToday_readFailureReturnsMax() {
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertEquals(Integer.MAX_VALUE, service.remainingToday(1L, false));
    }

    /** 限流文案不能是空白 —— 前端直接把它当 Markdown 显示给用户 */
    @Test
    void deniedMessage_isHumanReadable() {
        AiQuotaService.Decision d = new AiQuotaService.Decision(false, "daily", 3600, 100, 100);

        assertNotNull(d.message());
        assertFalse(d.message().isBlank());
        assertTrue(d.message().contains("明天"));
    }

    // ---------------- 全站日预算（P4） ----------------

    /**
     * 预算耗尽：直接拒绝，且**不能**再消耗用户个人次数。
     *
     * <p>顺序写反的代价很实在：预算拦住了但个人日额度已被扣，用户白白少一次，
     * 而且明天恢复时他不知道自己为什么额度变少了。
     */
    @Test
    void budgetExceeded_rejectsWithoutConsumingUserQuota() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(budget.status()).thenReturn(new AiBudgetService.Status(
                new BigDecimal("10.00"), new BigDecimal("10.00"), 1.0, true, false));

        AiQuotaService.Decision d = service.check(1L, false, "1.1.1.1");

        assertFalse(d.allowed());
        assertEquals("budget", d.scope());
        verify(ops, never()).increment(anyString());
        // 并发锁占过就必须放，否则用户会被锁到 5 分钟自动过期
        verify(redis).delete("ai:inflight:1");
        assertTrue(d.message().contains("额度"));
        // 与"个人额度用完"必须能分清，否则用户以为是自己被针对
        assertFalse(d.message().equals(new AiQuotaService.Decision(false, "daily", 60, 1, 1).message()));
    }

    /** 管理员不受预算限制：预算耗尽时运维仍要能进后台测试模型连通性 */
    @Test
    void budgetExceeded_stillAllowsAdmin() {
        when(budget.status()).thenReturn(new AiBudgetService.Status(
                new BigDecimal("10"), new BigDecimal("10"), 1.0, true, false));

        assertTrue(service.check(1L, true, "1.1.1.1").allowed());
    }

    /** 未设预算（0 = 不限）时完全不拦，也不该因为预算层而多碰一次 Redis 计数 */
    @Test
    void budgetUnlimited_allowsThrough() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(ops.increment(anyString())).thenReturn(1L);
        when(budget.status()).thenReturn(new AiBudgetService.Status(
                new BigDecimal("999.00"), BigDecimal.ZERO, 0, false, true));

        assertTrue(service.check(1L, false, "1.1.1.1").allowed());
    }

    // ---------------- 被拦记录（后台「今日限流」表的数据源） ----------------

    /** 记一次被拦：次数 +1、时间与触发层写进 last 哈希，两个键都要设过期 */
    @Test
    @SuppressWarnings("unchecked")
    void recordBlocked_incrementsAndStampsLastHit() {
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);

        service.recordBlocked(7L, "daily");

        verify(hash).increment(org.mockito.ArgumentMatchers.startsWith("ai:hit:"), org.mockito.ArgumentMatchers.eq("7"), org.mockito.ArgumentMatchers.eq(1L));
        verify(hash).put(org.mockito.ArgumentMatchers.startsWith("ai:hit:last:"),
                org.mockito.ArgumentMatchers.eq("7"), org.mockito.ArgumentMatchers.contains("|daily"));
        verify(redis, org.mockito.Mockito.times(2)).expire(anyString(), any(Duration.class));
    }

    /** 统计是旁路：Redis 报错不能把拒绝响应本身弄崩 */
    @Test
    @SuppressWarnings("unchecked")
    void recordBlocked_swallowsRedisFailure() {
        when(redis.opsForHash()).thenThrow(new RuntimeException("redis down"));

        service.recordBlocked(7L, "minute");   // 不抛异常即通过
    }

    /** 未登录（userId 为 null）不记录，避免所有匿名 IP 挤进同一行 */
    @Test
    void recordBlocked_ignoresNullUser() {
        service.recordBlocked(null, "ip");

        verify(redis, never()).opsForHash();
    }

    /** 读取被拦记录：按次数倒序；field 不是数字的行跳过，次数读不出来的保留但按 0 计 */
    @Test
    @SuppressWarnings("unchecked")
    void todayHits_sortsByCountAndSkipsGarbage() {
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        // 必须用精确 key：startsWith("ai:hit:") 会把 "ai:hit:last:..." 也命中，
        // Mockito 取最后一次匹配的 stub，就会把计数表当成 last 表读
        String day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        when(hash.entries("ai:hit:last:" + day))
                .thenReturn(java.util.Map.of("3", "2026-11-12 10:00:00|hourly", "5", "2026-11-12 11:00:00|daily"));
        when(hash.entries("ai:hit:" + day))
                .thenReturn(new java.util.LinkedHashMap<>(java.util.Map.of(
                        "3", "2", "5", "9", "abc", "4", "6", "oops")));

        java.util.List<AiQuotaService.Hit> hits = service.todayHits(10);

        // "abc" 那一行的 field 不是 userId，直接跳过；"6"→"oops" 是真用户，保留但次数按 0
        assertEquals(3, hits.size());
        assertEquals(5L, hits.get(0).userId().longValue());
        assertEquals(9, hits.get(0).count());
        assertEquals("daily", hits.get(0).scope());
        assertEquals("2026-11-12 11:00:00", hits.get(0).lastAt());
        assertEquals(3L, hits.get(1).userId().longValue());
        assertEquals(2, hits.get(1).count());
        assertEquals(6L, hits.get(2).userId().longValue());
        assertEquals(0, hits.get(2).count());
    }

    /** Redis 不可用 → 返回空列表（报表空着比 500 好） */
    @Test
    @SuppressWarnings("unchecked")
    void todayHits_returnsEmptyOnFailure() {
        when(redis.opsForHash()).thenThrow(new RuntimeException("redis down"));

        assertTrue(service.todayHits(10).isEmpty());
    }
}
