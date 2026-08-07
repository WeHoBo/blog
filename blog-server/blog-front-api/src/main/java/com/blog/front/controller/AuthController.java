package com.blog.front.controller;

import com.blog.common.dto.Result;
import com.blog.common.entity.User;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.UserMapper;
import com.blog.common.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public Result<LoginVO> me() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return Result.ok(new LoginVO(null, user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(), user.getRole()));
    }

    @PutMapping("/profile")
    public Result<?> updateProfile(@RequestBody User profile) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (profile.getNickname() != null) {
            user.setNickname(profile.getNickname());
        }
        if (profile.getAvatar() != null) {
            user.setAvatar(profile.getAvatar());
        }
        if (profile.getPassword() != null && !profile.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(profile.getPassword()));
        }
        userMapper.updateById(user);
        return Result.ok();
    }
}
