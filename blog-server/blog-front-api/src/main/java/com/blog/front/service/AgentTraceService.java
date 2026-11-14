package com.blog.front.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.entity.AgentTrace;
import com.blog.common.mapper.AgentTraceMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 轨迹落库 + 用量入账。
 *
 * <p>两件事放在**同一个事务**里：否则会出现「明细有、汇总没有」，
 * 后台的折线图和明细列表对不上，对账时查不出是谁的问题。
 *
 * <p>保留期靠运维清理（DDL 里只有索引，没有分区）：<br>
 * {@code DELETE FROM agent_trace WHERE create_time < DATE_SUB(NOW(), INTERVAL 90 DAY);}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceService {

    /** question 列是 TEXT，但模型上下文里的问题可能被拼得很长，这里截一刀防止写入失败 */
    private static final int MAX_QUESTION_CHARS = 2000;

    private final AgentTraceMapper traceMapper;
    private final AiUsageService usageService;

    /**
     * 写一条轨迹并同步记账。
     *
     * @return 新轨迹的 id；未登录（userId 为空）时不写，返回 null
     */
    @Transactional
    public Long save(TraceForm form) {
        if (form == null || form.getUserId() == null) {
            return null;
        }

        AgentTrace trace = new AgentTrace();
        trace.setUserId(form.getUserId());
        trace.setQuestion(cut(form.getQuestion(), MAX_QUESTION_CHARS));
        trace.setModel(cut(form.getModel(), 100));
        trace.setSteps(normalizeSteps(form.getSteps()));
        trace.setStepCount(Math.max(0, form.getStepCount()));
        trace.setPromptTokens((int) Math.max(0, form.getPromptTokens()));
        trace.setCompletionTokens((int) Math.max(0, form.getCompletionTokens()));
        trace.setTotalMs(Math.max(0, form.getTotalMs()));
        trace.setStatus(cut(normalizeStatus(form.getStatus()), 16));
        trace.setErrorMsg(cut(form.getErrorMsg(), 480));

        traceMapper.insert(trace);
        // 同事务记账；这里调 recordInTransaction 而不是 record —— 后者会吞异常，
        // 一旦记账失败就出现"明细有、汇总没有"，而且事务已经脏了还硬提交。
        usageService.recordInTransaction(form.getUserId(), form.getModel(),
                form.getPromptTokens(), form.getCompletionTokens());
        return trace.getId();
    }

    /**
     * 轨迹列表（后台排查用）。
     *
     * <p>只按 user/model/status 过滤 + 分页，不做全文检索：
     * question 是 TEXT，LIKE 会全表扫，而这个页面的用途是"看看刚才那次为什么慢/为什么错"，
     * 时间倒序翻几页就够了。
     */
    public Page<AgentTrace> page(int pageNum, int pageSize, Long userId, String model, String status) {
        LambdaQueryWrapper<AgentTrace> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(AgentTrace::getUserId, userId);
        }
        if (model != null && !model.isBlank()) {
            wrapper.eq(AgentTrace::getModel, model.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AgentTrace::getStatus, status.trim());
        }
        wrapper.orderByDesc(AgentTrace::getId);
        return traceMapper.selectPage(new Page<>(Math.max(1, pageNum), Math.min(100, Math.max(1, pageSize))), wrapper);
    }

    /**
     * steps 列是 MySQL JSON 类型，写进去非 JSON 文本会直接报错并拖垮整个事务。
     * 所以这里先校验，不合法就退化成空数组 —— 宁可丢步骤明细，也不能丢账单。
     */
    private String normalizeSteps(String steps) {
        if (steps == null || steps.isBlank()) {
            return "[]";
        }
        try {
            JSON.parseArray(steps);
            return steps;
        } catch (Exception e) {
            log.warn("Agent 轨迹 steps 不是合法 JSON，已丢弃：{}", steps.length() > 200 ? steps.substring(0, 200) : steps);
            return "[]";
        }
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "ok";
        }
        return switch (status.trim()) {
            case "timeout", "error", "aborted" -> status.trim();
            default -> "ok";
        };
    }

    private String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Data
    public static class TraceForm {
        private Long userId;
        private String question;
        private String model;
        private String steps;
        private int stepCount;
        private long promptTokens;
        private long completionTokens;
        private int totalMs;
        private String status;
        private String errorMsg;
    }
}
