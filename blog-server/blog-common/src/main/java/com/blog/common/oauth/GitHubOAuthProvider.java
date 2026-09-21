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
public class GitHubOAuthProvider implements OAuthProvider {

    @Value("${oauth.github.client-id}")
    private String clientId;

    @Value("${oauth.github.client-secret}")
    private String clientSecret;

    @Value("${oauth.github.proxy-host:}")
    private String proxyHost;

    @Value("${oauth.github.proxy-port:0}")
    private int proxyPort;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

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
        // redirect_uri 必须与 GitHub OAuth App 后台登记的 Callback URL 完全一致，
        // 否则 GitHub 会直接拒绝授权；这里做 URL 编码避免参数拼接异常。
        String redirectUri = URLEncoder.encode(baseUrl + "/api/auth/oauth/github/callback", StandardCharsets.UTF_8);
        return AUTHORIZE_URL + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&state=" + state
                + "&scope=user:email";
    }

    @Override
    public String getAccessToken(String code) {
        // code 含 + / = 等特殊字符，拼入 form body 前必须 URL 编码，
        // 否则 '+' 会被 form 解析为空格，导致 GitHub 返回 bad_verification_code。
        String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
        String body = "client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&code=" + encodedCode;
        String result;
        HttpResponse resp;
        try {
            resp = proxy(HttpRequest.post(TOKEN_URL))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body)
                    .execute();
            result = resp.body();
        } catch (Exception e) {
            // 网络层失败（DNS / 超时 / 连接重置）以前是完全静默的，排错成本极高，这里显式抛出并带上原因
            throw new BusinessException("GitHub token 请求失败（网络不通或超时）: " + e.getMessage());
        }
        JSONObject json;
        try {
            json = JSON.parseObject(result);
        } catch (Exception e) {
            throw new BusinessException("GitHub token 响应解析失败（HTTP " + resp.getStatus() + "）: "
                    + StrUtil.maxLength(result, 200));
        }
        String token = json == null ? null : json.getString("access_token");
        if (StrUtil.isBlank(token)) {
            // GitHub 失败时会返回 error / error_description，而不是 access_token
            String err = json == null ? null : json.getString("error");
            String errDesc = json == null ? null : json.getString("error_description");
            throw new BusinessException("GitHub token 获取失败: "
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
            HttpResponse resp = proxy(HttpRequest.get(USER_URL))
                    .header("Authorization", "token " + accessToken)
                    .header("User-Agent", "blog-app")
                    .execute();
            status = resp.getStatus();
            result = resp.body();
        } catch (Exception e) {
            throw new BusinessException("GitHub 用户信息请求失败: " + e.getMessage());
        }
        JSONObject json;
        try {
            json = JSON.parseObject(result);
        } catch (Exception e) {
            throw new BusinessException("GitHub 用户信息返回异常（HTTP " + status + "）: "
                    + StrUtil.maxLength(result, 200));
        }
        OAuthUser user = new OAuthUser();
        user.setOpenId(json.getString("id") != null ? json.getString("id") : json.getString("node_id"));
        if (StrUtil.isBlank(user.getOpenId())) {
            throw new BusinessException("GitHub 用户信息缺少 id（HTTP " + status + "）: "
                    + StrUtil.maxLength(result, 200));
        }
        user.setUsername(StrUtil.blankToDefault(json.getString("login"), "github_" + System.currentTimeMillis()));
        user.setNickname(StrUtil.blankToDefault(json.getString("name"), json.getString("login")));
        user.setAvatar(json.getString("avatar_url"));
        user.setEmail(json.getString("email"));
        return user;
    }
}
