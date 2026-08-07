package com.blog.front.service;

import com.blog.common.entity.Article;
import com.blog.common.entity.OperationLog;
import com.blog.common.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @AfterReturning(pointcut = "@annotation(org.springframework.web.bind.annotation.PostMapping) && " +
            "(execution(* com.blog.front.controller.ArticleController.create(..)) || " +
            "execution(* com.blog.front.controller.ArticleController.update*(..)) || " +
            "execution(* com.blog.front.controller.ArticleController.delete(..)))",
            returning = "result")
    public void logArticleOperation(JoinPoint joinPoint, Object result) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String method = joinPoint.getSignature().getName();
            String action = method.contains("delete") ? "删除" : method.contains("update") ? "修改" : "新增";
            if (result instanceof com.blog.common.dto.Result) {
                com.blog.common.dto.Result<?> r = (com.blog.common.dto.Result<?>) result;
                if (r.getData() instanceof Article) {
                    Article article = (Article) r.getData();
                    OperationLog log = new OperationLog();
                    log.setUserId(userId);
                    log.setAction(action + "文章: " + article.getTitle());
                    log.setTargetId(article.getId());
                    log.setTargetType("article");
                    operationLogMapper.insert(log);
                }
            }
        } catch (Exception ignored) {}
    }
}
