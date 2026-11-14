package com.blog.front.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.blog.common.dto.Result;
import com.blog.front.service.AiQuotaService;
import com.blog.front.service.AiUsageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 问答代理：转发前端请求到本地 RAG 服务（Python，127.0.0.1:8000）。
 *
 * /chat     Agent 问答（SSE 流式：status / tool / token / sources / done / error）
 * /free     自由问答（无工具，同样走 SSE，前端只需一套解析）
 * /quota    今日剩余额度
 *
 * 鉴权由 SecurityConfig 的 /api/** authenticated 统一控制（需登录）。
 * 配额（三层窗口 + 并发锁 + IP 兜底）在本层判定 —— RAG 侧看不到 JWT，不知道谁是管理员。
 *
 * ⚠️ SSE 响应体由 Python 生成，Java 只做字节透传：在本层解析事件再重组，
 * 会引入半包/粘包问题，还平白多一层 CPU 开销。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    @Value("${ai.rag-url:http://127.0.0.1:8000}")
    private String ragUrl;

    /** 与 RAG 服务共享的调用令牌（RAG_API_TOKEN）；为空时不附加请求头（兼容未开启鉴权的旧部署） */
    @Value("${ai.rag-token:}")
    private String ragToken;

    /** 当前使用的模型标识，透传给 RAG 记账用；P2 接入模型注册表后由配置决定 */
    @Value("${ai.model-code:deepseek-chat}")
    private String modelCode;

    private final AiQuotaService quotaService;
    private final AiUsageService usageService;
    private final com.blog.front.service.AiModelService modelService;

    // ---------------- 对外接口 ----------------

    /** RAG 流式问答（检索博客文章后回答） */
    @PostMapping("/chat")
    public StreamingResponseBody chat(@RequestBody(required = false) String rawBody,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        return guarded("/chat", rawBody, request, response);
    }

    /** 自由问答（不检索：代码解释 / 全文总结等） */
    @PostMapping("/free")
    public StreamingResponseBody free(@RequestBody(required = false) String rawBody,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        return guarded("/free-chat", rawBody, request, response);
    }

    /** 今日剩余额度（前端在面板打开时拉一次，避免聊到一半突然被拦） */
    @GetMapping("/quota")
    public Result<Map<String, Object>> quota() {
        Long userId = currentUserId();
        boolean admin = isAdmin();
        Map<String, Object> data = new HashMap<>(usageService.todaySummary(userId));
        data.put("unlimited", admin);
        data.put("remaining", admin ? -1 : quotaService.remainingToday(userId, false));
        data.put("limit", admin ? -1 : quotaService.dailyLimit());
        return Result.ok(data);
    }

    /** 获取 RAG 已入库文档列表（调试用） */
    @GetMapping("/documents")
    public Result<?> documents() {
        try {
            HttpResponse resp = withAuth(HttpRequest.get(ragUrl + "/documents"))
                    .timeout(10000)
                    .execute();
            try {
                return Result.ok(com.alibaba.fastjson2.JSON.parse(resp.body()));
            } finally {
                resp.close();
            }
        } catch (Exception e) {
            return Result.fail(500, "RAG 服务不可用: " + e.getMessage());
        }
    }

    // ---------------- 内部实现 ----------------

    /**
     * 配额检查 + 转发。
     *
     * ⚠️ userId / 角色必须在**返回 lambda 之前**解析好：
     * StreamingResponseBody 在另一个线程执行，那里拿不到 SecurityContext。
     */
    private StreamingResponseBody guarded(String path, String rawBody,
                                          HttpServletRequest request, HttpServletResponse response) {
        Long userId = currentUserId();
        boolean admin = isAdmin();
        AiQuotaService.Decision decision = quotaService.check(userId, admin, clientIp(request));

        prepareStreamResponse(response);

        if (!decision.allowed()) {
            // 统计用旁路：后台「今日限流」表据此回答"用量为什么变少了"
            quotaService.recordBlocked(userId, decision.scope());
            // 前端只解析 SSE，裸 429 会变成"请求失败"，用户看不懂。
            // 这里仍返回 200 + text/event-stream，立刻发一个 error 事件（设计文档 §12.4）。
            response.setHeader("X-AI-Quota-Exceeded", decision.scope());
            response.setHeader("X-AI-Retry-After", String.valueOf(decision.retryAfterSeconds()));
            return out -> {
                writeSseError(out, "RATE_LIMIT", decision.message(),
                        decision.scope(), decision.retryAfterSeconds());
                out.flush();
            };
        }

        return forward(path, rawBody, userId);
    }

    private StreamingResponseBody forward(String path, String rawBody, Long userId) {
        final String payload = (rawBody == null || rawBody.isBlank()) ? "{}" : rawBody;

        return outputStream -> {
            HttpResponse resp = null;
            try {
                HttpRequest req = withAuth(HttpRequest.post(ragUrl + path))
                        .header("Content-Type", "application/json");
                // RAG 记完用量回调 /internal/ai/usage 时需要知道是谁、用的哪个模型
                if (userId != null) {
                    req.header("X-AI-User-Id", String.valueOf(userId));
                }
                req.header("X-AI-Model", currentModelCode());

                resp = req.body(payload.getBytes(StandardCharsets.UTF_8))
                        .timeout(300000)
                        .execute();

                int status = resp.getStatus();
                if (status != 200) {
                    String detail = resp.body();
                    if (detail == null || detail.isBlank()) {
                        detail = "AI 服务异常(HTTP " + status + ")";
                    }
                    writeSseError(outputStream, "UPSTREAM", detail, null, 0);
                    outputStream.flush();
                    return;
                }
                // SSE 是 UTF-8 字节流，原样透传即可 —— 在 Java 里解析再重组只会引入
                // 半包/粘包问题，还多一层 CPU 开销
                try (InputStream in = resp.bodyStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        outputStream.write(buf, 0, n);
                        outputStream.flush();   // 不 flush 会被缓冲住，看起来像"不流式"
                    }
                }
            } catch (Exception e) {
                log.warn("AI 转发中断: {}", e.getMessage());
                try {
                    writeSseError(outputStream, "NETWORK", "AI 服务连接中断，请稍后重试", null, 0);
                    outputStream.flush();
                } catch (Exception ignored) {
                }
            } finally {
                if (resp != null) {
                    resp.close();
                }
                // 无论成功、失败、客户端中止，都必须解锁，否则用户会被卡 5 分钟
                quotaService.release(userId);
            }
        };
    }

    private HttpRequest withAuth(HttpRequest req) {
        if (ragToken != null && !ragToken.isBlank()) {
            req.header("X-RAG-Token", ragToken);
        }
        return req;
    }

    /**
     * 当前生效模型的标识，用于用量归属。
     *
     * <p>以模型注册表为准（后台切了模型，账要记在新模型名下），
     * 注册表不可用时才回落到配置里的 ai.model-code。
     */
    private String currentModelCode() {
        try {
            com.blog.front.service.AiModelService.ActiveModel active = modelService.active();
            if (active != null && active.modelId() != null && !active.modelId().isBlank()) {
                return active.modelId();
            }
        } catch (Exception e) {
            log.debug("读取生效模型失败，用量归属回落到配置值: {}", e.getMessage());
        }
        return modelCode;
    }

    private void prepareStreamResponse(HttpServletResponse httpResponse) {
        httpResponse.setContentType("text/event-stream;charset=utf-8");
        httpResponse.setHeader("X-Accel-Buffering", "no");
        httpResponse.setHeader("Cache-Control", "no-cache");
        // 明确禁用缓冲，否则部分代理会攒够一个 buffer 才吐给浏览器
        httpResponse.setHeader("Connection", "keep-alive");
    }

    /** 按 SSE 格式写一个 error 事件；客户端据此显示限流/异常文案，而不是统一的"请求失败" */
    private void writeSseError(OutputStream outputStream, String code, String message,
                               String scope, long retryAfterSeconds) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        if (scope != null) {
            payload.put("scope", scope);
        }
        if (retryAfterSeconds > 0) {
            payload.put("retryAfter", retryAfterSeconds);
            payload.put("resetAt", LocalDateTime.now().plusSeconds(retryAfterSeconds).toString());
        }
        String frame = "event: error\ndata: " + JSON.toJSONString(payload) + "\n\n";
        outputStream.write(frame.getBytes(StandardCharsets.UTF_8));
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            return null;
        }
        return id;
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
    }

    /**
     * 取真实客户端 IP。
     * Nginx 反代后 RemoteAddr 恒为 127.0.0.1，必须取 X-Forwarded-For 的第一跳，
     * 否则 IP 维度的兜底限流会把所有访客算成同一个人。
     */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
