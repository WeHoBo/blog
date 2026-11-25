package com.blog.front.service;

import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.entity.Comment;
import com.blog.common.entity.OperationLog;
import com.blog.common.mapper.OperationLogMapper;
import com.blog.front.config.ApiAccessLogFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 操作审计（写进 {@code operation_log} 表，不是文本日志 —— 文本日志会滚掉、没法查询）。
 *
 * <p><b>为什么用 {@code @Around} 而不是 {@code @AfterReturning}</b>：
 * 旧实现只在方法<b>成功返回</b>后写一行，失败的操作完全不留痕。可是审核制下最该查的
 * 恰恰是「谁试过但被拒绝了」—— 比如某个 author 反复去改别人的文章。
 * 改成环绕之后，成功/失败都记，还能顺手把耗时量出来。
 *
 * <p><b>为什么要记 {@code status}</b>：光有「操作名」看不出结果。
 * 同样是「删除文章」，成功和「这不是你的文章」在事后完全无法区分。
 *
 * <p><b>为什么要记 {@code requestId}</b>：后台看到一条失败审计后，能直接拿这个 id 去
 * {@code blog-api-access.log} / {@code blog-api-error.log} 里 grep，把当时那次请求
 * 的前后文（状态码、耗时、堆栈）全部捞出来。没有它，两条链路是断开的。
 *
 * <p><b>为什么删除日志必须跟着方法走完</b>：旧实现把删除挂在 {@code @Before}，
 * 于是「删除被拒绝」也会写一条「删除文章 id=123」—— 审计表会撒谎。
 *
 * <p><b>为什么写库失败只记 ERROR 不回滚业务</b>：审计是旁路。它挂了不该让人发不出文章。
 * 但必须打<b>带堆栈</b>的 ERROR —— 旧代码只打 {@code e.getMessage()}，
 * 等于「审计坏了但你不知道坏在哪行」。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public void recordSiteWrite(HttpServletRequest request, Long userId, int status, long costMs) {
        try {
            boolean upload = "/api/upload".equals(request.getRequestURI());
            boolean success = status >= 200 && status < 300;
            String action = upload ? "上传素材" : "保存站点设置";
            OperationLog row = new OperationLog();
            row.setUserId(userId);
            row.setAction(success ? action : action + "（HTTP " + status + "）");
            row.setTargetType(upload ? "upload" : "site_config");
            row.setStatus(success ? 1 : 0);
            row.setIp(ApiAccessLogFilter.clientIp(request));
            row.setRequestId((String) request.getAttribute(ApiAccessLogFilter.RID_ATTR));
            row.setCostMs(costMs);
            operationLogMapper.insert(row);
        } catch (Exception e) {
            log.error("站点操作审计写入失败 rid={}", request.getAttribute(ApiAccessLogFilter.RID_ATTR), e);
        }
    }

    /**
     * 需要审计的写操作。
     *
     * <p>⚠️ 加新的写接口时记得往这里补切点，否则那条路径**完全不会留痕**。
     * 历史上就漏过 {@code AuthorController} —— 它是和 {@code ArticleController} 平行的
     * 第二条写入路径，漏掉之后作者写的每一篇文章都没有审计。
     */
    @Pointcut("execution(* com.blog.front.controller.ArticleController.create(..)) || " +
            "execution(* com.blog.front.controller.ArticleController.update(..)) || " +
            "execution(* com.blog.front.controller.ArticleController.delete(..)) || " +
            "execution(* com.blog.front.controller.ArticleController.approve(..)) || " +
            "execution(* com.blog.front.controller.ArticleController.reject(..)) || " +
            "execution(* com.blog.front.controller.AuthorController.create(..)) || " +
            "execution(* com.blog.front.controller.AuthorController.update(..)) || " +
            "execution(* com.blog.front.controller.AuthorController.delete(..)) || " +
            "execution(* com.blog.front.controller.CommentController.create(..)) || " +
            "execution(* com.blog.front.controller.CommentAdminController.delete(..)) || " +
            "execution(* com.blog.front.controller.CommentAdminController.handleReport(..)) || " +
            // 用户管理：改角色 = 提权、改密码 = 能冒用别人账号，这两件事必须留痕。
            // 之前这一整类都没进切点，等于「谁在什么时候把自己提成管理员」查不到。
            "execution(* com.blog.front.controller.AdminUserController.update(..)) || " +
            "execution(* com.blog.front.controller.AdminUserController.delete(..))")
    public void writeOperations() {
    }

    @Around("writeOperations()")
    public Object auditWrite(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        boolean success = true;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            try {
                write(pjp, result, success, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 旁路失败绝不影响业务，但必须留堆栈，否则"审计静默失效"没人发现
                log.error("审计写入失败（业务不受影响）: {}", pjp.getSignature().toShortString(), e);
            }
        }
    }

    /**
     * 答题通过、授予 author 角色。
     *
     * <p>只记「授予成功」的那一次：答错是正常业务结果，每次都记只会把审计表刷满噪音。
     * 但这条记录很重要 —— 它是一次<b>无人值守的提权</b>，必须能回答「这个人什么时候、
     * 靠哪套题拿到发布权限的」。
     */
    @AfterReturning(pointcut = "execution(* com.blog.front.service.ApplyService.submit(..))",
            returning = "ret")
    public void auditApplyGrant(JoinPoint jp, Object ret) {
        try {
            if (!(ret instanceof Map<?, ?> map)) {
                return;
            }
            if (!Boolean.TRUE.equals(map.get("passed"))) {
                return;
            }
            OperationLog row = base("授予作者权限（答题通过）", "user",
                    jp.getArgs().length > 0 && jp.getArgs()[0] instanceof Long id ? id : currentUserId());
            row.setStatus(1);
            operationLogMapper.insert(row);
        } catch (Exception e) {
            log.error("授予权限审计写入失败: {}", jp.getSignature().toShortString(), e);
        }
    }

    // =================================================================
    // 内部
    // =================================================================

    private void write(ProceedingJoinPoint pjp, Object result, boolean success, long costMs) {
        String method = pjp.getSignature().getName();
        String declaring = pjp.getSignature().getDeclaringType().getSimpleName();

        String targetType = targetTypeOf(declaring, method);
        String action = actionOf(declaring, method, targetType, success);
        Long targetId = targetIdOf(result, pjp.getArgs());

        OperationLog row = base(action, targetType, currentUserId());
        row.setTargetId(targetId);
        row.setStatus(success ? 1 : 0);
        row.setCostMs(costMs);
        operationLogMapper.insert(row);
    }

    /**
     * 组装公共字段（用户、IP、链路 ID）。
     *
     * <p>IP 在这里存<b>原值</b>：审计是安全证据，打码就没法定位来源了。
     * 文本日志那侧才打码（见 {@link ApiAccessLogFilter#maskIp}）。
     */
    private OperationLog base(String action, String targetType, Long userId) {
        OperationLog row = new OperationLog();
        row.setUserId(userId);
        row.setAction(action);
        row.setTargetType(targetType);
        row.setIp(clientIp());
        row.setRequestId(requestId());
        return row;
    }

    private String actionOf(String declaring, String method, String targetType, boolean success) {
        boolean author = "AuthorController".equals(declaring);
        boolean userAdmin = "AdminUserController".equals(declaring);
        boolean comment = "comment".equals(targetType);
        String action = switch (method) {
            case "create" -> author ? "作者提交文章" : (comment ? "发表评论" : "新增文章");
            case "update" -> userAdmin ? "修改用户" : (author ? "作者修改文章" : "修改文章");
            case "delete" -> userAdmin ? "删除用户" : (comment ? "删除评论" : "删除文章");
            case "approve" -> "审核通过";
            case "reject" -> "审核驳回";
            case "handleReport" -> "处理举报";
            default -> "其他写操作";
        };
        // 失败时在动作后面标一下，后台列表一眼能看出来
        return success ? action : action + "（失败）";
    }

    private String targetTypeOf(String declaring, String method) {
        // 用户管理必须单独归一类：混进 article 的话，审计表会写成「删除文章 target_id=8」，
        // 而实际删的是一个用户 —— 错的审计比没有审计更危险。
        if ("AdminUserController".equals(declaring)) {
            return "user";
        }
        if ("CommentController".equals(declaring) || "CommentAdminController".equals(declaring)) {
            return "handleReport".equals(method) ? "comment_report" : "comment";
        }
        return "article";
    }

    /**
     * 目标 ID：优先从返回值里取（新增类接口只能这样拿到新 ID），
     * 其次取第一个 Long 入参（update/delete/approve 的第一个参数都是 id）。
     */
    private Long targetIdOf(Object result, Object[] args) {
        if (result instanceof Result<?> r) {
            if (r.getData() instanceof Article article) {
                return article.getId();
            }
            if (r.getData() instanceof Comment comment) {
                return comment.getId();
            }
        }
        for (Object arg : args) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        return null;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return null;
        }
        return userId;
    }

    private String requestId() {
        String rid = MDC.get("rid");
        if (rid != null && !rid.isBlank()) {
            return rid;
        }
        ServletRequestAttributes attrs = requestAttributes();
        if (attrs != null && attrs.getResponse() != null) {
            return attrs.getResponse().getHeader(ApiAccessLogFilter.RID_HEADER);
        }
        return null;
    }

    private String clientIp() {
        ServletRequestAttributes attrs = requestAttributes();
        if (attrs == null || attrs.getRequest() == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return ApiAccessLogFilter.clientIp(request);
    }

    private ServletRequestAttributes requestAttributes() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra ? sra : null;
    }
}
