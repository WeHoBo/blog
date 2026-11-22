package com.blog.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码：手机号 + 验证码 + 新密码。
 *
 * <p>这是权限最高的一条路径 —— 拿到手机验证码就能改掉密码。
 * 所以它必须：① 走 RESET 场景的验证码（不能拿登录验证码来用）；
 * ② 改完之后不签发 token（让用户重新登录一次，避免密码被改后旧会话继续存在）。
 */
@Data
public class PasswordResetDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度 6-30 位")
    private String newPassword;
}
