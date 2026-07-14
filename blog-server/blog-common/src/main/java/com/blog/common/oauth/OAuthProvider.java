package com.blog.common.oauth;

import com.blog.common.dto.OAuthUser;

public interface OAuthProvider {
    String getProviderName();
    String getAuthorizeUrl(String state);
    String getAccessToken(String code);
    OAuthUser getUserInfo(String accessToken);
}
