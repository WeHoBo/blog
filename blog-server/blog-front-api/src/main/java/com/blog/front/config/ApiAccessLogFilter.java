package com.blog.front.config;

import com.blog.front.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 接口摘要日志 + 链路 ID（requestId）。
 *
 * <p><b>这是什么</b>：每个 {@code /api/**} 请求结束后只打<b>一行</b> INFO，
 * 内容是「方法 路径 状态码 耗时 用户 IP」。不打请求正文、不打返回体 ——
 * 长文本一旦进日志，磁盘和检索都会被拖死，而且正文里全是用户隐私。
 *
 * <p><b>为什么排在过滤器链最外层</b>（{@link Ordered#HIGHEST_PRECEDENCE}）：
 * 只有最外层才能把「Spring Security 拦下的 403」「参数绑定失败」「全局异常处理器吐出的 500」
 * 统统包住。放在中间层的话，被安全链直接拒绝的请求一行日志都不会留下 ——
 * 那正是最需要留痕的越权探测。
 *
 * <p><b>为什么用 request attribute 取 userId 而不是 SecurityContextHolder</b>：
 * 本过滤器在 Security 链的外面，等 {@code chain.doFilter} 返回时
 * Spring Security 已经在自己的 finally 里把上下文清空了，这里读一定是 null。
 * 所以 {@code JwtAuthenticationFilter} 认证成功后会把 userId 塞进
 * {@link #UID_ATTR}，请求对象活到最后，读得到。
 *
 * <p><b>为什么不记 query string</b>：OAuth 回调是
 * {@code /api/auth/oauth/gitee?code=xxx&state=xxx}，那个 code 是一次性凭证，
 * 等价于密码。摘要日志只记 path，不记参数，从根上避免泄漏。
 *
 * <p><b>不记哪些请求</b>：
 * <ul>
 *   <li>{@code /api/ai/**} —— SSE 长连接，耗时以十秒计，混进访问日志会把
 *       「接口响应时间」这个指标彻底污染；它另有自己的 done/tool 事件日志。</li>
 *   <li>阅读数打点 —— 每次翻页一条，纯噪音，量还大。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ApiAccessLogFilter extends OncePerRequestFilter {
    private final OperationLogService operationLogService;

    /** 独立 logger：方便在 logback 里单独指定文件、级别、保留天数 */
    private static final Logger ACCESS = LoggerFactory.getLogger("ACCESS");

    public static final String RID_HEADER = "X-Request-Id";
    public static final String RID_ATTR = "blog.rid";
    /** JwtAuthenticationFilter 认证成功后写入的 userId 属性名 */
    public static final String UID_ATTR = "blog.uid";
    private static final String MDC_RID = "rid";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rid = resolveRid(request);
        // 回写响应头：前端报错时把 X-Request-Id 一起贴过来，就能直接 grep 到这一次请求
        response.setHeader(RID_HEADER, rid);
        request.setAttribute(RID_ATTR, rid);
        MDC.put(MDC_RID, rid);

        long start = System.currentTimeMillis();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            // 逃出全局异常处理器的异常（响应已提交、容器级错误等）也要算失败
            failed = true;
            throw e;
        } finally {
            long costMs = System.currentTimeMillis() - start;
            try {
                int status = failed ? 500 : response.getStatus();
                if (shouldLog(request)) {
                    ACCESS.info("{} {} status={} {}ms uid={} ip={}", request.getMethod(),
                            request.getRequestURI(), status, costMs,
                            request.getAttribute(UID_ATTR) == null ? "-" : request.getAttribute(UID_ATTR),
                            maskIp(clientIp(request)));
                }
                if ("POST".equals(request.getMethod()) && request.getAttribute(UID_ATTR) instanceof Long userId) {
                    // Multipart 解析可能先于控制器失败。按最终 HTTP 结果只写一次审计。
                    String path = request.getRequestURI();
                    if ("/api/upload".equals(path) || "/api/site-config/save".equals(path)) {
                        operationLogService.recordSiteWrite(request, userId, status, costMs);
                    }
                }
            } finally {
                MDC.remove(MDC_RID);
            }
        }
    }

    /**
     * 是否记录这一条。只记 {@code /api/**}，并排除 SSE 与阅读打点。
     */
    private boolean shouldLog(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            return false;
        }
        if (uri.startsWith("/api/ai/")) {
            return false;
        }
        // 阅读数打点：GET /api/article/{id}/view
        return !uri.matches("^/api/article/\\d+/view$");
    }

    /**
     * 复用上游传来的 requestId（网关/前端重试时能串起来），没有就新生成。
     *
     * <p>只截取 36 字符，防止有人用超长 header 把日志行撑爆。
     */
    private String resolveRid(HttpServletRequest request) {
        String incoming = request.getHeader(RID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            String trimmed = incoming.trim();
            return trimmed.length() > 36 ? trimmed.substring(0, 36) : trimmed;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 真实客户端 IP。经 nginx 反代后 {@code getRemoteAddr()} 永远是 127.0.0.1，
     * 必须取 {@code X-Forwarded-For} 的第一段。
     */
    public static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 文件日志里的 IP 打码：保留网段便于判断来源，隐去主机位。
     *
     * <p>入库的审计记录<b>不</b>打码 —— 那是内部安全排查要用的原值，只有后台可见。
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "-";
        }
        int idx = ip.lastIndexOf('.');
        if (idx > 0) {
            return ip.substring(0, idx + 1) + "*";
        }
        // IPv6：只留前两段
        String[] parts = ip.split(":");
        if (parts.length >= 2) {
            return parts[0] + ":" + parts[1] + ":*";
        }
        return "*";
    }
}
