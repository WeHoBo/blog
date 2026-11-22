package com.blog.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 已登录状态下设置 / 修改密码。
 *
 * <p>{@code oldPassword} 在 {@code password_set=0}（OAuth 自动建号、密码只是随机占位）
 * 时可以不传 —— 那批用户根本不知道自己的密码是什么，逼他们提供旧密码等于永久锁死。
 * 但一旦已经设过（{@code password_set=1}），改密码就必须验旧密码，
 * 否则任何拿到已登录设备的人都能直接把账号密码改掉。
 */
@Data
public class PasswordChangeDTO {

    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度 6-30 位")
    private String newPassword;
}
