package com.blog.front.controller;

import com.blog.common.dto.Result;
import com.blog.front.service.AgentTraceService;
import com.blog.front.service.AiModelService;
import com.blog.front.service.AiToolService;
import com.blog.front.service.AiUsageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部接口：只允许本机 RAG 服务调用。
 *
 * ⚠️ 三重防护，缺一不可：
 *   1. Nginx 层 `location /internal/ { deny all; return 404; }`（公网不可达）
 *   2. SecurityConfig 里 permitAll（RAG 没有 JWT，不能用 authenticated）
 *   3. 本类内校验 X-RAG-Token == ai.rag-token（最后一道）
 *
 * 少任何一层，`/internal/**` 都可能被外网直接打进来。
 */
@Slf4j
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class InternalAiController {

    @Value("${ai.rag-token:}")
    private String ragToken;

    private final AiUsageService usageService;
    private final AiToolService toolService;
    private final AgentTraceService traceService;
    private final AiModelService modelService;

    /**
     * 当前生效的模型配置（含解密后的密钥）。
     *
     * <p>只在回环上调用，且 Nginx 已把 /internal/ 全段拒掉，密钥不会出现在任何公网响应里。
     *
     * <p>返回 {@code data = null} 表示注册表里没有可用模型，RAG 应回落到自己的 .env ——
     * 这条兜底让"后台配错了"不至于把全站 AI 打挂。
     *
     * <p>{@code apiKey} 与 {@code apiKeyEnv} 互斥：env 模式下 Java 只把**变量名**传下去，
     * 由 RAG 读它自己的 .env。不能让 Java 去解析环境变量 —— key 通常只配在 RAG 那边。
     */
    @GetMapping("/model")
    public Result<Map<String, Object>> activeModel(@RequestHeader(value = "X-RAG-Token", required = false) String token) {
        if (!tokenOk(token)) {
            return Result.fail(401, "无效的调用令牌");
        }
        AiModelService.ActiveModel m = modelService.active();
        if (m == null) {
            return Result.ok(null);
        }
        Map<String, Object> data = toMap(m);
        // 降级模型（P4）：和主模型一起下发，RAG 不必再发一次请求。
        // 显式写 null 而不是省略键 —— 抓包时能一眼看出"是没配降级"，而不是"字段丢了"。
        AiModelService.ActiveModel fb = modelService.fallback();
        data.put("fallback", fb == null ? null : toMap(fb));
        return Result.ok(data);
    }

    private Map<String, Object> toMap(AiModelService.ActiveModel m) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", m.id());
        data.put("displayName", m.displayName());
        data.put("provider", m.provider());
        data.put("modelId", m.modelId());
        data.put("baseUrl", m.baseUrl());
        data.put("apiKey", m.apiKey());
        data.put("apiKeyEnv", m.apiKeyEnv());
        data.put("temperature", m.temperature());
        data.put("maxTokens", m.maxTokens());
        data.put("contextWindow", m.contextWindow());
        data.put("supportTools", m.supportTools());
        data.put("inputPrice", m.inputPrice());
        data.put("outputPrice", m.outputPrice());
        return data;
    }

    /**
     * RAG 流结束后回调，上报本轮真实 token 用量。
     *
     * <p>P0 起正常路径改用 {@link #reportTrace}（明细+汇总同事务），
     * 这个接口保留给「流程中途异常、只拿到部分 token」的场景。
     */
    @PostMapping("/usage")
    public Result<?> reportUsage(@RequestHeader(value = "X-RAG-Token", required = false) String token,
                                 @RequestBody UsageForm form) {
        if (!tokenOk(token)) {
            return Result.fail(401, "无效的调用令牌");
        }
        if (form == null || form.getUserId() == null) {
            return Result.ok();
        }
        usageService.record(form.getUserId(), form.getModelCode(),
                form.getPromptTokens(), form.getCompletionTokens());
        return Result.ok();
    }

    /**
     * 工具网关：Agent 循环里的每一次工具调用都打到这里。
     *
     * <p>返回 {@code {ok, digest, data, sources}}；工具自身的业务失败也走 200，
     * 由 ok=false + error 表达 —— 让模型看到错误文本自己换路子，而不是整轮崩掉。
     */
    @PostMapping("/tool/{name}")
    public Result<Map<String, Object>> tool(@RequestHeader(value = "X-RAG-Token", required = false) String token,
                                            @PathVariable String name,
                                            @RequestBody(required = false) ToolForm form) {
        if (!tokenOk(token)) {
            return Result.fail(401, "无效的调用令牌");
        }
        Map<String, Object> args = form == null ? Map.of() : form.getArgs();
        return Result.ok(toolService.execute(name, args));
    }

    /** 一轮问答结束后写轨迹 + 记账（Java 侧同事务完成） */
    @PostMapping("/trace")
    public Result<?> reportTrace(@RequestHeader(value = "X-RAG-Token", required = false) String token,
                                 @RequestBody AgentTraceService.TraceForm form) {
        if (!tokenOk(token)) {
            return Result.fail(401, "无效的调用令牌");
        }
        try {
            Long id = traceService.save(form);
            return Result.ok(id == null ? null : Map.of("traceId", id));
        } catch (Exception e) {
            // 轨迹/记账写失败不能反过来让已经答完的用户看到报错
            log.warn("Agent 轨迹落库失败（不影响用户）: {}", e.getMessage());
            return Result.fail(500, "轨迹写入失败");
        }
    }

    /**
     * 令牌为空时放行 —— 与 RAG 侧「未配置 RAG_API_TOKEN 则不校验」的行为保持一致，
     * 仅用于本机开发；生产必须两侧都配上。
     */
    private boolean tokenOk(String token) {
        if (ragToken == null || ragToken.isBlank()) {
            return true;
        }
        if (ragToken.equals(token)) {
            return true;
        }
        log.warn("收到无效的 RAG 内部调用令牌，已拒绝");
        return false;
    }

    @Data
    public static class UsageForm {
        private Long userId;
        private String modelCode;
        private long promptTokens;
        private long completionTokens;
    }

    @Data
    public static class ToolForm {
        private Map<String, Object> args;
    }
}
