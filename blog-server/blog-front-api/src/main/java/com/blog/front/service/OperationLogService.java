package com.blog.front.service;

import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.entity.OperationLog;
import com.blog.common.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @AfterReturning(pointcut = "execution(* com.blog.front.controller.ArticleController.create(..)) || " +
            "execution(* com.blog.front.controller.ArticleController.update(..))",
            returning = "result")
    public void logCreateUpdate(JoinPoint joinPoint, Object result) {
        try {
            if (!(result instanceof Result<?> r) || !(r.getData() instanceof Article article)) {
                return;
            }
            String method = joinPoint.getSignature().getName();
            String action = "update".equals(method) ? "修改" : "新增";
            insertLog(action + "文章: " + article.getTitle(), article.getId());
        } catch (Exception e) {
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }

    @Before("execution(* com.blog.front.controller.ArticleController.delete(..))")
    public void logDelete(JoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            Long id = args.length > 0 && args[0] instanceof Long ? (Long) args[0] : null;
            insertLog("删除文章: id=" + id, id);
        } catch (Exception e) {
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }

    private void insertLog(String action, Long targetId) {
        OperationLog operationLog = new OperationLog();
        operationLog.setUserId(currentUserId());
        operationLog.setAction(action);
        operationLog.setTargetId(targetId);
        operationLog.setTargetType("article");
        operationLogMapper.insert(operationLog);
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return null;
        }
        return userId;
    }
}
