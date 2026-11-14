package com.blog.front.service;

import com.blog.common.entity.AiSetting;
import com.blog.common.mapper.AiUsageDailyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 全站预算的边界测试。
 *
 * <p>这块的错法有两种，都很贵：
 * <ul>
 *   <li>该拦不拦 —— key 被盗刷时没有任何护栏，账单直接失控；</li>
 *   <li>不该拦却拦 —— 预算配成 0（不限）却把全站 AI 停了。</li>
 * </ul>
 */
class AiBudgetServiceTest {

    private AiUsageDailyMapper usageMapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private AiModelService modelService;
    private AiBudgetService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        usageMapper = mock(AiUsageDailyMapper.class);
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        modelService = mock(AiModelService.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        service = new AiBudgetService(usageMapper, redis, modelService);
    }

    private void settingWithBudget(String budget) {
        AiSetting s = new AiSetting();
        s.setGlobalDailyBudget(new BigDecimal(budget));
        when(modelService.settingCached()).thenReturn(s);
    }

    @SuppressWarnings("unchecked")
    private void dbCost(String cost) {
        when(usageMapper.selectObjs(any())).thenReturn(List.of(new BigDecimal(cost)));
    }

    /** 预算 0 = 不限：这是默认值，绝不能因此把全站 AI 停掉 */
    @Test
    void zeroBudget_meansUnlimited() {
        settingWithBudget("0");
        dbCost("88.00");

        AiBudgetService.Status st = service.status();

        assertTrue(st.unlimited());
        assertFalse(st.exceeded());
        assertEquals(0.0, st.ratio(), 0.0001);
    }

    /** 没有 setting 记录（表还没建/被删）同样按不限处理 */
    @Test
    void missingSetting_meansUnlimited() {
        when(modelService.settingCached()).thenReturn(null);
        dbCost("88.00");

        assertTrue(service.status().unlimited());
    }

    /** 刚好达到预算就算超支（>= 而不是 >）——否则最后一点额度会被无限使用 */
    @Test
    void reachingBudget_exactly_isExceeded() {
        settingWithBudget("10.00");
        dbCost("10.000000");

        AiBudgetService.Status st = service.status();

        assertFalse(st.unlimited());
        assertTrue(st.exceeded());
        assertEquals(1.0, st.ratio(), 0.0001);
    }

    @Test
    void belowBudget_notExceeded_ratioReported() {
        settingWithBudget("10.00");
        dbCost("2.50");

        AiBudgetService.Status st = service.status();

        assertFalse(st.exceeded());
        assertEquals(0.25, st.ratio(), 0.0001);
        assertEquals(0, new BigDecimal("2.5").compareTo(st.spent()));
    }

    /** 缓存命中时不该再查库：这是"每个请求都过一次预算"的成本前提 */
    @Test
    @SuppressWarnings("unchecked")
    void cachedCost_skipsDatabase() {
        when(ops.get(anyString())).thenReturn("3.20");
        settingWithBudget("10.00");

        AiBudgetService.Status st = service.status();

        assertFalse(st.exceeded());
        verify(usageMapper, never()).selectObjs(any());
    }

    /** 未命中时查库并回写缓存（带过期时间，否则键会永久留着） */
    @Test
    @SuppressWarnings("unchecked")
    void miss_queriesDbAndCachesWithTtl() {
        dbCost("4.00");
        settingWithBudget("10.00");

        service.status();

        verify(ops).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * 数据库异常 → 按 0 处理（fail-open）。
     *
     * <p>这是刻意的：预算熔断是省钱手段，不能因为库抖一下就把全站 AI 停掉。
     * 与 AiQuotaService 的故障策略保持一致。
     */
    @Test
    @SuppressWarnings("unchecked")
    void dbFailure_failsOpen() {
        when(usageMapper.selectObjs(any())).thenThrow(new RuntimeException("db down"));
        settingWithBudget("10.00");

        AiBudgetService.Status st = service.status();

        assertFalse(st.exceeded());
        assertEquals(0, BigDecimal.ZERO.compareTo(st.spent()));
    }

    /** Redis 读失败也要能继续（改查数据库），不能因为缓存挂了就不判预算 */
    @Test
    @SuppressWarnings("unchecked")
    void redisFailure_stillQueriesDb() {
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));
        dbCost("12.00");
        settingWithBudget("10.00");

        assertTrue(service.status().exceeded());
    }

    /** SUM 返回 null（今天没人用过）按 0 处理，不能 NPE */
    @Test
    @SuppressWarnings("unchecked")
    void emptySum_treatedAsZero() {
        when(usageMapper.selectObjs(any())).thenReturn(List.of());
        settingWithBudget("10.00");

        assertEquals(0, BigDecimal.ZERO.compareTo(service.status().spent()));
    }
}
