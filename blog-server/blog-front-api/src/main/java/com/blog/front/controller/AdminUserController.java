package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.Result;
import com.blog.common.entity.User;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理：改角色 / 改密码 / 禁用账号。
 *
 * <p><b>整类只给超管</b>（{@code hasRole('super')}）。这是超级管理员和普通管理员的核心分界线 ——
 * 能进这个页面，就等于能把任何一个账号提成管理员、也能改别人密码，
 * 所以它不能和「写文章」同级别。
 *
 * <p>类里另有三道防呆，都是为了防止「手滑把自己锁死」：<br>
 * ① 角色只能在 admin / user 之间切，super 只能走 SQL 授予（否则后台上点多两下就多一个超管）；<br>
 * ② 不能改自己的角色 / 状态（一降级就再也进不来，且没人是超管了）；<br>
 * ③ 不能动另一个 super（理论上只有一个，但数据被人手工写脏时别让它继续烂下去）。
 */
@RestController
@RequestMapping("/api/admin/user")
@PreAuthorize("hasRole('super')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    public Result<Page<User>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(User::getUsername, keyword).or().like(User::getNickname, keyword);
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        page.getRecords().forEach(u -> u.setPassword(null)); // 脱敏
        return Result.ok(page);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody User form) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");

        Long me = currentUserId();
        boolean isSelf = me != null && me.equals(id);
        boolean targetIsSuper = "super".equals(user.getRole());

        // 只有"真的要改"才校验：前端每次都把整个表单发上来，值没变就不该拦
        if (form.getRole() != null && !form.getRole().trim().equals(user.getRole())) {
            String role = form.getRole().trim();
            if (!"admin".equals(role) && !"user".equals(role)) {
                throw new BusinessException(400, "角色只能是 admin 或 user；超级管理员请在数据库里维护");
            }
            if (isSelf) throw new BusinessException(400, "不能修改自己的角色（会把自己锁在门外）");
            if (targetIsSuper) throw new BusinessException(400, "不能修改超级管理员的角色");
            user.setRole(role);
        }

        if (form.getStatus() != null && !form.getStatus().equals(user.getStatus())) {
            if (isSelf) throw new BusinessException(400, "不能禁用自己的账号");
            if (targetIsSuper) throw new BusinessException(400, "不能禁用超级管理员");
            user.setStatus(form.getStatus());
        }

        if (form.getNickname() != null) user.setNickname(form.getNickname());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        userMapper.updateById(user);
        return Result.ok();
    }

    /** 当前登录用户 id（JwtAuthenticationFilter 把 userId 放进了 principal） */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }
}
