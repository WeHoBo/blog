package com.blog.front.service;

import com.blog.common.mapper.AiUsageDailyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 记账口径测试。
 *
 * 重点是金额：这里算错不会报错，只会让月底账单和后台数字对不上，
 * 而且发现时已经过去一个月了，所以必须在写入时就锁住公式。
 */
class AiUsageServiceTest {

    private AiUsageDailyMapper mapper;
    private AiUsageService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AiUsageDailyMapper.class);
        // 单价优先查模型注册表；这里给 null，走 application.yml 的默认价
        AiModelService modelService = mock(AiModelService.class);
        when(modelService.priceByModelId(anyString())).thenReturn(null);
        service = new AiUsageService(mapper, modelService);
        ReflectionTestUtils.setField(service, "defaultModelCode", "deepseek-chat");
        ReflectionTestUtils.setField(service, "inputPrice", new BigDecimal("2.0"));
        ReflectionTestUtils.setField(service, "outputPrice", new BigDecimal("8.0"));
    }

    /** 100 万输入 + 50 万输出 = 2.0 + 4.0 = 6.0 元 */
    @Test
    void record_computesCostFromTokenPrices() {
        service.record(1L, "deepseek-chat", 1_000_000, 500_000);

        ArgumentCaptor<BigDecimal> cost = ArgumentCaptor.forClass(BigDecimal.class);
        verify(mapper).upsertAdd(any(LocalDate.class), eq(1L), eq("deepseek-chat"),
                eq(1_000_000L), eq(500_000L), cost.capture());
        assertEquals(0, new BigDecimal("6.000000").compareTo(cost.getValue()));
    }

    /** 模型标识为空时回落默认值，否则统计里会出现一条空模型的脏数据 */
    @Test
    void record_fallsBackToDefaultModel() {
        service.record(1L, "  ", 10, 10);

        verify(mapper).upsertAdd(any(LocalDate.class), eq(1L), eq("deepseek-chat"),
                eq(10L), eq(10L), any(BigDecimal.class));
    }

    @Test
    void record_skipsWhenUserMissing() {
        service.record(null, "deepseek-chat", 100, 100);

        verify(mapper, never()).upsertAdd(any(), anyLong(), anyString(), anyLong(), anyLong(), any());
    }

    /** 记账失败绝不能冒泡到用户请求上（用户已经看到答案了） */
    @Test
    void record_swallowsStorageFailure() {
        when(mapper.upsertAdd(any(), anyLong(), anyString(), anyLong(), anyLong(), any()))
                .thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.record(1L, "deepseek-chat", 10, 10));
    }

    /** 负数 token 要按 0 处理，避免出现负金额把当月总量冲低 */
    @Test
    void record_clampsNegativeTokens() {
        service.record(1L, "deepseek-chat", -100, -100);

        verify(mapper).upsertAdd(any(LocalDate.class), eq(1L), eq("deepseek-chat"),
                eq(0L), eq(0L), any(BigDecimal.class));
    }
}
