package com.blog.common.oauth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.blog.common.dto.OAuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitHubOAuthProvider implements OAuthProvider {

    @Value("${oauth.github.client-id}")
    private String clientId;

    @Value("${oauth.github.client-secret}")
    private String clientSecret;

    @Value("${oauth.github.proxy-host:}")
    private String proxyHost;

    @Value("${oauth.github.proxy-port:0}")
    private int proxyPort;

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";

    private HttpRequest proxy(HttpRequest req) {
        if (StrUtil.isNotBlank(proxyHost) && proxyPort > 0) {
            req.setHttpProxy(proxyHost, proxyPort);
        }
        return req;
    }

    @Override
    public String getProviderName() {
        return "github";
    }

    @Override
    public String getAuthorizeUrl(String state) {
        return AUTHORIZE_URL + "?client_id=" + clientId + "&state=" + state + "&scope=user:email";
    }

    @Override
    public String getAccessToken(String code) {
        String body = "client_id=" + clientId + "&client_secret=" + clientSecret + "&code=" + code;
        String result = proxy(HttpRequest.post(TOKEN_URL))
                .header("Accept", "application/json")
                .body(body)
                .execute()
                .body();
        JSONObject json = JSON.parseObject(result);
        return json.getString("access_token");
    }

    @Override
    public OAuthUser getUserInfo(String accessToken) {
        String result = proxy(HttpRequest.get(USER_URL))
                .header("Authorization", "token " + accessToken)
                .header("User-Agent", "blog-app")
                .execute()
                .body();
        JSONObject json = JSON.parseObject(result);
        OAuthUser user = new OAuthUser();
        user.setOpenId(json.getString("id") != null ? json.getString("id") : json.getString("node_id"));
        user.setUsername(StrUtil.blankToDefault(json.getString("login"), "github_" + System.currentTimeMillis()));
        user.setNickname(StrUtil.blankToDefault(json.getString("name"), json.getString("login")));
        user.setAvatar(json.getString("avatar_url"));
        user.setEmail(json.getString("email"));
        return user;
    }
}
