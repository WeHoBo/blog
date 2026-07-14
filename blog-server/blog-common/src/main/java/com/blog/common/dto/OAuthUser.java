package com.blog.common.dto;

import lombok.Data;

@Data
public class OAuthUser {
    private String openId;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
}
