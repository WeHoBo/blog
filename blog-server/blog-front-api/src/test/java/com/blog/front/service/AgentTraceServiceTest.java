package com.blog.front.service;

import com.blog.common.entity.AgentTrace;
import com.blog.common.mapper.AgentTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 轨迹写入的边界测试。
 *
 * 核心担忧是"记账被明细拖死"：steps 是 MySQL JSON 列，写进去非法 JSON 会直接
 * 让整个事务失败，连带用量也记不上 —— 那就会出现账单缺口。
 */
class AgentTraceServiceTest {

    private AgentTraceMapper traceMapper;
    private AiUsageService usageService;
    private AgentTraceService service;

    @BeforeEach
    void setUp() {
        traceMapper = mock(AgentTraceMapper.class);
        usageService = mock(AiUsageService.class);
        service = new AgentTraceService(traceMapper, usageService);
        when(traceMapper.insert(any(AgentTrace.class))).thenReturn(1);
    }

    /** 未登录（没有 userId）时不写：agent_trace.user_id 是 NOT NULL，硬写必炸 */
    @Test
    void nullUser_skipsEverything() {
        Long id = service.save(form(null));

        assertNull(id);
        verify(traceMapper, never()).insert(any(AgentTrace.class));
        verify(usageService, never()).recordInTransaction(any(), any(), anyLong(), anyLong());
    }

    /** 正常路径：明细与用量用同一组 token 数，不能一个多一个少 */
    @Test
    void validTrace_persistsAndBills() {
        AgentTraceService.TraceForm form = form(5L);
        form.setPromptTokens(1200);
        form.setCompletionTokens(300);
        form.setStepCount(2);
        form.setStatus("ok");

        service.save(form);

        ArgumentCaptor<AgentTrace> captor = ArgumentCaptor.forClass(AgentTrace.class);
        verify(traceMapper).insert(captor.capture());
        AgentTrace saved = captor.getValue();
        assertEquals(5L, saved.getUserId().longValue());
        assertEquals(1200, saved.getPromptTokens().intValue());
        assertEquals(300, saved.getCompletionTokens().intValue());
        assertEquals(2, saved.getStepCount().intValue());
        assertEquals("ok", saved.getStatus());
        verify(usageService).recordInTransaction(5L, "deepseek-chat", 1200, 300);
    }

    /** ⭐ 非法 steps 要退化成 []，但账单必须照记 */
    @Test
    void invalidStepsJson_degradesButStillBills() {
        AgentTraceService.TraceForm form = form(6L);
        form.setSteps("{这不是 JSON");

        service.save(form);

        ArgumentCaptor<AgentTrace> captor = ArgumentCaptor.forClass(AgentTrace.class);
        verify(traceMapper).insert(captor.capture());
        assertEquals("[]", captor.getValue().getSteps());
        verify(usageService).recordInTransaction(any(), anyString(), anyLong(), anyLong());
    }

    /** 合法 JSON 原样保留 */
    @Test
    void validStepsJson_keptAsIs() {
        AgentTraceService.TraceForm form = form(7L);
        String steps = "[{\"step\":1,\"tool\":\"search_articles\",\"ok\":true}]";
        form.setSteps(steps);

        service.save(form);

        ArgumentCaptor<AgentTrace> captor = ArgumentCaptor.forClass(AgentTrace.class);
        verify(traceMapper).insert(captor.capture());
        assertEquals(steps, captor.getValue().getSteps());
    }

    /** 状态白名单之外的一律归一到 ok，避免脏值进库后报表统计错 */
    @Test
    void unknownStatus_normalizedToOk() {
        AgentTraceService.TraceForm form = form(8L);
        form.setStatus("weird");

        service.save(form);

        ArgumentCaptor<AgentTrace> captor = ArgumentCaptor.forClass(AgentTrace.class);
        verify(traceMapper).insert(captor.capture());
        assertEquals("ok", captor.getValue().getStatus());
    }

    /** aborted 要保留 —— 中止的调用花了 token 但没交付答案，这是重要的排查信号 */
    @Test
    void abortedStatus_kept() {
        AgentTraceService.TraceForm form = form(9L);
        form.setStatus("aborted");

        service.save(form);

        ArgumentCaptor<AgentTrace> captor = ArgumentCaptor.forClass(AgentTrace.class);
        verify(traceMapper).insert(captor.capture());
        assertEquals("aborted", captor.getValue().getStatus());
    }

    /** 超长问题要截断，否则 TEXT 列写入失败会拖垮整个事务 */
    @Test
    void longQuestion_truncated() {
        AgentTraceService.TraceForm form = form(10L);
        form.setQuestion("问".repeat(5000));

        service.save(form);

        ArgumentCaptor<AgentTrace> captor = ArgumentCaptor.forClass(AgentTrace.class);
        verify(traceMapper).insert(captor.capture());
        assertEquals(2000, captor.getValue().getQuestion().length());
    }

    private AgentTraceService.TraceForm form(Long userId) {
        AgentTraceService.TraceForm f = new AgentTraceService.TraceForm();
        f.setUserId(userId);
        f.setQuestion("什么是 RAG");
        f.setModel("deepseek-chat");
        f.setSteps("[]");
        f.setStepCount(0);
        f.setPromptTokens(100);
        f.setCompletionTokens(50);
        f.setTotalMs(800);
        f.setStatus("ok");
        return f;
    }
}
