package com.blog.front.controller;

import com.blog.common.dto.Result;
import com.blog.common.entity.User;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.UserMapper;
import com.blog.common.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;

    @GetMapping("/me")
    public Result<LoginVO> me() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return Result.ok(new LoginVO(null, user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(), user.getBio(), user.getRole()));
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
        // 注意：这里刻意不处理密码。账号唯一登录方式是 OAuth（GitHub/Gitee/华为），
        // 密码登录已下线；若在此继续写密码，只会产生一个永远用不上的凭证。
        if (profile.getAvatar() != null) {
            user.setAvatar(profile.getAvatar());
        }
        // 简介由用户自己写（管理员也一样）。空串是「我要清空」，null 是「这次不改」，
        // 所以这里用 != null 判断而不是 isNotBlank —— 否则用户清不掉原来的简介。
        if (profile.getBio() != null) {
            String bio = profile.getBio().trim();
            if (bio.length() > 200) {
                throw new BusinessException(400, "个人简介不能超过 200 字");
            }
            user.setBio(bio);
        }
        userMapper.updateById(user);
        return Result.ok();
    }
}
