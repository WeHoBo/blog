package com.blog.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户名 + 密码登录。
 *
 * <p>2026-09-11 的提交 {@code 40ffa5c} 曾把它当"死代码"删掉（当时登录已改为纯 OAuth）。
 * 现在恢复并接回密码登录主路径 —— 类名与字段保持不变，方便与历史代码对照。
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
