package com.blog.front.controller;

import com.blog.common.dto.ApplySubmitForm;
import com.blog.common.dto.Result;
import com.blog.front.service.ApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 「答题申请发布权限」的用户侧接口 —— 首页那个「申请管理员发布文章」按钮用的就是这两个。
 *
 * <p>⚠️ 必须挂 {@code isAuthenticated()}：{@code SecurityConfig} 的兜底规则是
 * {@code anyRequest().permitAll()}，**不加注解就等于匿名可访问**。
 * 这条是踩过坑的：URL 层放行 ≠ 方法能访问，反过来也成立 —— URL 层放行之后，
 * 方法层什么都不写就是真的谁都能进。
 *
 * <p>⚠️ 返回体里**没有任何正确答案**，判分全在 {@link ApplyService} 里做。
 */
@RestController
@RequestMapping("/api/apply")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ApplyController {

    private final ApplyService applyService;

    /** 题目（不含答案）+ 通过题数 + 剩余次数 + 我当前的角色 */
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        return Result.ok(applyService.info(currentUserId()));
    }

    /**
     * 提交答案。通过时返回新的 token —— 前端必须用它替换本地 token，
     * 否则旧 token 里还是 role=user，刷新页面依然没权限。
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody(required = false) ApplySubmitForm form) {
        return Result.ok(applyService.submit(currentUserId(), form == null ? null : form.getAnswers()));
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            return null;
        }
        return id;
    }
}
