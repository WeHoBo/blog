package com.blog.front.controller;

import com.blog.common.dto.Result;
import com.blog.common.entity.ApplyQuestion;
import com.blog.common.entity.ApplySetting;
import com.blog.front.service.ApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 「答题申请」的配置接口，**只有超管能用**。
 *
 * <p>用 super 而不是 admin：题目与通过线决定了谁能拿到作者权限，
 * 等于一把"发钥匙的钥匙"，不该下放给普通管理员。
 *
 * <p>⚠️ 这里返回的题目**带 answer**（超管要能看见正确答案才好编辑），
 * 而用户侧的 {@code /api/apply/info} 不带。两个接口分开写正是为了这条边界 ——
 * 要是图省事复用一个接口，正确答案就顺着公开接口漏出去了。
 */
@RestController
@RequestMapping("/api/admin/apply")
@PreAuthorize("hasRole('super')")
@RequiredArgsConstructor
public class AdminApplyController {

    private final ApplyService applyService;

    @GetMapping("/questions")
    public Result<List<ApplyQuestion>> questions() {
        return Result.ok(applyService.listAll());
    }

    /** 新增（无 id）或更新（带 id） */
    @PostMapping("/questions")
    public Result<ApplyQuestion> save(@RequestBody ApplyQuestion form) {
        return Result.ok(applyService.saveQuestion(form));
    }

    @DeleteMapping("/questions/{id}")
    public Result<?> delete(@PathVariable Long id) {
        applyService.deleteQuestion(id);
        return Result.ok();
    }

    @GetMapping("/setting")
    public Result<ApplySetting> setting() {
        return Result.ok(applyService.getSetting());
    }

    /**
     * 只改通过题数与开关。
     *
     * <p>body 用 Map 而不是实体：如果直接收 {@code ApplySetting}，
     * 前端一传 {@code id} 就能改到别的行（虽然目前只有一行），
     * 显式取两个字段更稳。
     */
    @PutMapping("/setting")
    public Result<ApplySetting> saveSetting(@RequestBody Map<String, Object> body) {
        Integer passCount = intOrNull(body.get("passCount"));
        Integer enabled = intOrNull(body.get("enabled"));
        return Result.ok(applyService.saveSetting(passCount, enabled));
    }

    private Integer intOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
