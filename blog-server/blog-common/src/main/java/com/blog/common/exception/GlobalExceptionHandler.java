package com.blog.common.exception;

import com.blog.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 全局异常处理。
 *
 * <p><b>日志纪律</b>（改这里之前先读）：
 * <ul>
 *   <li>每条异常日志都必须带 {@code rid=}、方法、路径。<b>没有 rid 的异常日志等于没打</b> ——
 *       线上并发出错时，你无法判断哪一行属于哪次请求，更无法和访问日志对上。</li>
 *   <li>异常对象本身（{@code e}）只作为最后一个参数传入，<b>不要</b>用
 *       {@code e.getMessage()} 代替 —— 那样会丢掉堆栈，等于放弃了行号。</li>
 *   <li>不同级别区分清楚：业务异常/参数错误/越权是 <b>WARN</b>（正常业务流，不该触发告警）；
 *       只有真的系统故障才用 <b>ERROR</b>。否则 ERROR 日志被噪音淹没，就没人看了。</li>
 * </ul>
 *
 * <p><b>状态码有个坑</b>：{@link BusinessException} 没有 {@code @ResponseStatus}，
 * 所以它返回的是 <b>HTTP 200 + body.code=400</b>。这意味着访问日志里的 status 会是 200，
 * 光看访问日志会以为「一片绿」。要抓业务失败，必须看这里打的 code，或者看审计表的 status。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        // 业务异常是「可预期」的失败（未审核、无权修改、参数不合规），不是故障 → WARN
        log.warn("业务异常 rid={} {} {} code={} msg={}",
                rid(), method(), uri(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        // 以前这里一行日志都不打，等于「参数错了但查不到是谁错的」→ 补 WARN
        log.warn("参数校验失败 rid={} {} {} msg={}", rid(), method(), uri(), msg);
        return Result.fail(400, msg);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleAccessDeniedException(AccessDeniedException e) {
        // 🔴 越权探测是安全事件：必须留痕，尤其是 author 角色去探 admin 接口这种情况。
        // 以前这里静默返回 403，日志里毫无痕迹。
        log.warn("权限不足 rid={} {} {} uid={}", rid(), method(), uri(), uid());
        return Result.fail(403, "无权限访问");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        // 只有这里保留完整堆栈：堆栈是唯一能定位到行号的东西，一个字都不截
        log.error("系统异常 rid={} {} {}", rid(), method(), uri(), e);
        return Result.fail("服务器内部错误");
    }

    // =================================================================
    // 取请求上下文（异常处理器不在 Filter 里，只能从 RequestContextHolder 拿）
    // =================================================================

    private static ServletRequestAttributes attrs() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra ? sra : null;
    }

    /**
     * 链路 ID。优先读 MDC（与访问日志同一个线程），回落读响应头
     * （异步分发时 MDC 可能已被清理，但响应头还在）。
     */
    private static String rid() {
        String rid = MDC.get("rid");
        if (rid != null && !rid.isBlank()) {
            return rid;
        }
        ServletRequestAttributes sra = attrs();
        if (sra != null) {
            HttpServletResponse response = sra.getResponse();
            if (response != null) {
                String header = response.getHeader("X-Request-Id");
                if (header != null && !header.isBlank()) {
                    return header;
                }
            }
        }
        return "-";
    }

    private static String method() {
        ServletRequestAttributes sra = attrs();
        if (sra == null) {
            return "-";
        }
        HttpServletRequest request = sra.getRequest();
        return request == null ? "-" : request.getMethod();
    }

    /** 只取 path，不带 query string —— OAuth 回调的 code 是一次性凭证，不能进日志 */
    private static String uri() {
        ServletRequestAttributes sra = attrs();
        if (sra == null) {
            return "-";
        }
        HttpServletRequest request = sra.getRequest();
        return request == null ? "-" : request.getRequestURI();
    }

    private static Object uid() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return "-";
        }
        return userId;
    }
}
