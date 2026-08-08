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
import java.util.Base64;

@Component
public class HuaweiOAuthProvider implements OAuthProvider {

    @Value("${oauth.huawei.client-id}")
    private String clientId;

    @Value("${oauth.huawei.client-secret}")
    private String clientSecret;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final String AUTHORIZE_URL = "https://oauth-login.cloud.huawei.com/oauth2/v3/authorize";
    private static final String TOKEN_URL = "https://oauth-login.cloud.huawei.com/oauth2/v3/token";
    private static final String USER_URL = "https://oauth-login.cloud.huawei.com/oauth2/v1/userinfo";

    /** 缓存最近一次 token 响应中的 id_token（JWT），getUserInfo 优先从其中解码用户信息 */
    private volatile String lastIdToken;

    private String redirectUri() {
        return baseUrl + "/api/auth/oauth/huawei/callback";
    }

    private String encodedRedirectUri() {
        return URLEncoder.encode(redirectUri(), StandardCharsets.UTF_8);
    }

    @Override
    public String getProviderName() {
        return "huawei";
    }

    @Override
    public String getAuthorizeUrl(String state) {
        return AUTHORIZE_URL + "?response_type=code&client_id=" + clientId
                + "&redirect_uri=" + encodedRedirectUri()
                + "&scope=openid%20profile&state=" + state;
    }

    @Override
    public String getAccessToken(String code) {
        // code 含 + / = 等特殊字符，拼入 form body 前必须 URL 编码，否则 + 会被解析为空格
        String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
        String body = "grant_type=authorization_code&client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&code=" + encodedCode
                + "&redirect_uri=" + encodedRedirectUri();
        String result;
        try {
            result = HttpRequest.post(TOKEN_URL)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body)
                    .execute()
                    .body();
        } catch (Exception e) {
            throw new BusinessException("华为 token 请求失败: " + e.getMessage());
        }
        JSONObject json = JSON.parseObject(result);
        String token = json.getString("access_token");
        if (StrUtil.isBlank(token)) {
            throw new BusinessException("华为 token 获取失败: " + json.getString("error_description"));
        }
        // 诊断：打印 token 响应完整结构（脱敏）
        StringBuilder debug = new StringBuilder("[huawei-oauth] token 响应 {");
        for (String k : json.keySet()) {
            String v = json.getString(k);
            if (v != null && v.length() > 16 && ("access_token".equals(k) || "id_token".equals(k) || "refresh_token".equals(k))) {
                v = v.substring(0, 8) + "...";
            }
            debug.append(k).append("=").append(v).append("; ");
        }
        System.out.println(debug + "}");
        String idToken = json.getString("id_token");
        if (StrUtil.isNotBlank(idToken)) {
            lastIdToken = idToken;
        }
        return token;
    }

    @Override
    public OAuthUser getUserInfo(String accessToken) {
        // 优先从 id_token (JWT) 解码用户信息
        if (StrUtil.isNotBlank(lastIdToken)) {
            try {
                return parseUserFromIdToken(lastIdToken);
            } catch (Exception e) {
                System.out.println("[huawei-oauth] id_token 解析失败: " + e.getMessage());
            }
        } else {
            System.out.println("[huawei-oauth] token 响应无 id_token，回退 userinfo 接口");
        }
        return parseUserFromUserInfoApi(accessToken);
    }

    private OAuthUser parseUserFromIdToken(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new BusinessException("华为 id_token 格式错误");
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        JSONObject json = JSON.parseObject(payloadJson);
        OAuthUser user = new OAuthUser();
        user.setOpenId(json.getString("openID") != null ? json.getString("openID") : json.getString("sub"));
        if (StrUtil.isBlank(user.getOpenId())) {
            throw new BusinessException("华为 id_token 缺少 openID");
        }
        user.setUsername("huawei_" + user.getOpenId());
        String nickname = StrUtil.blankToDefault(json.getString("displayName"), null);
        if (StrUtil.isBlank(nickname)) {
            String id = user.getOpenId();
            nickname = "华为用户_" + (id != null && id.length() >= 4 ? id.substring(id.length() - 4) : "0000");
        }
        user.setNickname(nickname);
        user.setAvatar(json.getString("headPictureURL"));
        user.setEmail(json.getString("email"));
        return user;
    }

    private OAuthUser parseUserFromUserInfoApi(String accessToken) {
        String result;
        int status = 0;
        try {
            HttpResponse resp = HttpRequest.get(USER_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .execute();
            status = resp.getStatus();
            result = resp.body();
            System.out.println("[huawei-oauth] userinfo HTTP " + status + ": " + StrUtil.maxLength(result, 150));
        } catch (Exception e) {
            throw new BusinessException("华为用户信息请求失败: " + e.getMessage());
        }
        JSONObject json;
        try {
            json = JSON.parseObject(result);
        } catch (Exception e) {
            throw new BusinessException("华为用户信息返回异常: " + StrUtil.maxLength(result, 200));
        }
        if (json == null || StrUtil.isBlank(json.getString("openID"))) {
            throw new BusinessException("华为用户信息获取失败: " + StrUtil.maxLength(result, 200));
        }
        OAuthUser user = new OAuthUser();
        user.setOpenId(json.getString("openID"));
        user.setUsername("huawei_" + user.getOpenId());
        String nickname = StrUtil.blankToDefault(json.getString("displayName"),
                StrUtil.blankToDefault(json.getString("name"), null));
        if (StrUtil.isBlank(nickname)) {
            String id = user.getOpenId();
            nickname = "华为用户_" + (id != null && id.length() >= 4 ? id.substring(id.length() - 4) : "0000");
        }
        user.setNickname(nickname);
        user.setAvatar(json.getString("headPictureURL"));
        user.setEmail(json.getString("email"));
        return user;
    }
}
