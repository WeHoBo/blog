package com.blog.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册：用户名 + 密码 + 手机号 + 短信验证码。
 *
 * <p>手机号是必填的，且必须通过验证码验证 —— 这是整条链路里唯一能证明
 * "这个号码确实是本人的"的环节。没验证过就入库，等于允许任何人
 * 用别人的手机号注册，后续找回密码环节会直接把账号交到别人手里。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度 3-20 位")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "用户名只能包含字母、数字、下划线、短横线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度 6-30 位")
    private String password;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    /** 昵称可选；留空时用用户名顶上 */
    @Size(max = 50, message = "昵称不能超过 50 字")
    private String nickname;
}
