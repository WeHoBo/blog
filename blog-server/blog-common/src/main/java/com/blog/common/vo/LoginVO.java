package com.blog.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    /** 个人简介，用户在个人中心自己写 */
    private String bio;
    private String role;
}
