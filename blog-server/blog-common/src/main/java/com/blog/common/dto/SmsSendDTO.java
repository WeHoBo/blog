package com.blog.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 请求发送短信验证码 */
@Data
public class SmsSendDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 用途：register / login / reset / bind。
     * 后端会做白名单转换，未知值直接拒绝 —— 不能让前端随便传个字符串
     * 就在 Redis 里造出一个新的 key 前缀。
     */
    @NotBlank(message = "用途不能为空")
    private String scene;
}
