package com.blog.common.oauth;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.blog.common.dto.OAuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GiteeOAuthProvider implements OAuthProvider {

    @Value("${oauth.gitee.client-id}")
    private String clientId;

    @Value("${oauth.gitee.client-secret}")
    private String clientSecret;

    private static final String AUTHORIZE_URL = "https://gitee.com/oauth/authorize";
    private static final String TOKEN_URL = "https://gitee.com/oauth/token";
    private static final String USER_URL = "https://gitee.com/api/v5/user";

    @Override
    public String getProviderName() {
        return "gitee";
    }

    @Override
    public String getAuthorizeUrl(String state) {
        return AUTHORIZE_URL + "?client_id=" + clientId
                + "&redirect_uri=" + "http://localhost:8080/api/auth/oauth/gitee/callback"
                + "&response_type=code&state=" + state;
    }

    @Override
    public String getAccessToken(String code) {
        String body = "grant_type=authorization_code&code=" + code
                + "&client_id=" + clientId + "&client_secret=" + clientSecret
                + "&redirect_uri=" + "http://localhost:8080/api/auth/oauth/gitee/callback";
        String result = HttpRequest.post(TOKEN_URL).body(body).execute().body();
        JSONObject json = JSON.parseObject(result);
        return json.getString("access_token");
    }

    @Override
    public OAuthUser getUserInfo(String accessToken) {
        String result = HttpRequest.get(USER_URL + "?access_token=" + accessToken).execute().body();
        JSONObject json = JSON.parseObject(result);
        OAuthUser user = new OAuthUser();
        user.setOpenId(json.getString("id") != null ? json.getString("id") : json.getString("login"));
        user.setUsername("gitee_" + json.getString("login"));
        user.setNickname(GiteeOAuthProvider.nullToDefault(json.getString("name"), json.getString("login")));
        user.setAvatar(json.getString("avatar_url"));
        user.setEmail(json.getString("email"));
        return user;
    }

    public static String nullToDefault(String value, String defaultVal) {
        return value != null && !value.isEmpty() ? value : defaultVal;
    }
}
