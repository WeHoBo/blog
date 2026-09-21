package com.blog.common.oauth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.blog.common.dto.OAuthUser;
import com.blog.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class GiteeOAuthProvider implements OAuthProvider {

    @Value("${oauth.gitee.client-id}")
    private String clientId;

    @Value("${oauth.gitee.client-secret}")
    private String clientSecret;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final String AUTHORIZE_URL = "https://gitee.com/oauth/authorize";
    private static final String TOKEN_URL = "https://gitee.com/oauth/token";
    private static final String USER_URL = "https://gitee.com/api/v5/user";

    @Override
    public String getProviderName() {
        return "gitee";
    }

    @Override
    public String getAuthorizeUrl(String state) {
        String redirectUri = URLEncoder.encode(baseUrl + "/api/auth/oauth/gitee/callback", StandardCharsets.UTF_8);
        return AUTHORIZE_URL + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code&state=" + state;
    }

    @Override
    public String getAccessToken(String code) {
        // 与 GitHub 同理：code 里的 '+' 若不做 URL 编码，会被 form body 解析成空格导致校验失败
        String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
        String redirectUri = URLEncoder.encode(baseUrl + "/api/auth/oauth/gitee/callback", StandardCharsets.UTF_8);
        String body = "grant_type=authorization_code&code=" + encodedCode
                + "&client_id=" + clientId + "&client_secret=" + clientSecret
                + "&redirect_uri=" + redirectUri;
        String result;
        HttpResponse resp;
        try {
            resp = HttpRequest.post(TOKEN_URL)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body)
                    .execute();
            result = resp.body();
        } catch (Exception e) {
            throw new BusinessException("Gitee token 请求失败（网络不通或超时）: " + e.getMessage());
        }
        JSONObject json;
        try {
            json = JSON.parseObject(result);
        } catch (Exception e) {
            throw new BusinessException("Gitee token 响应解析失败（HTTP " + resp.getStatus() + "）: "
                    + StrUtil.maxLength(result, 200));
        }
        String token = json == null ? null : json.getString("access_token");
        if (StrUtil.isBlank(token)) {
            String err = json == null ? null : json.getString("error");
            String errDesc = json == null ? null : json.getString("error_description");
            throw new BusinessException("Gitee token 获取失败: "
                    + (err != null ? err : "未知错误")
                    + (errDesc != null ? "（" + errDesc + "）" : ""));
        }
        return token;
    }

    @Override
    public OAuthUser getUserInfo(String accessToken) {
        String result;
        int status;
        try {
            HttpResponse resp = HttpRequest.get(USER_URL + "?access_token=" + accessToken)
                    .header("User-Agent", "blog-app")
                    .execute();
            status = resp.getStatus();
            result = resp.body();
        } catch (Exception e) {
            throw new BusinessException("Gitee 用户信息请求失败: " + e.getMessage());
        }
        JSONObject json;
        try {
            json = JSON.parseObject(result);
        } catch (Exception e) {
            throw new BusinessException("Gitee 用户信息返回异常（HTTP " + status + "）: "
                    + StrUtil.maxLength(result, 200));
        }
        OAuthUser user = new OAuthUser();
        user.setOpenId(json.getString("id") != null ? json.getString("id") : json.getString("login"));
        if (StrUtil.isBlank(user.getOpenId())) {
            throw new BusinessException("Gitee 用户信息缺少 id（HTTP " + status + "）: "
                    + StrUtil.maxLength(result, 200));
        }
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
