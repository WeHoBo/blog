package com.blog.front.util;

import com.blog.common.oauth.HuaweiOAuthProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HuaweiOAuthProviderTest {

    @Test
    void authorizeUrl_containsRequiredParams() {
        HuaweiOAuthProvider provider = new HuaweiOAuthProvider();
        // 通过反射注入测试值，避免 Spring 依赖
        try {
            var clientIdField = HuaweiOAuthProvider.class.getDeclaredField("clientId");
            clientIdField.setAccessible(true);
            clientIdField.set(provider, "test-client-id");
            var baseUrlField = HuaweiOAuthProvider.class.getDeclaredField("baseUrl");
            baseUrlField.setAccessible(true);
            baseUrlField.set(provider, "https://codeup.asia");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String url = provider.getAuthorizeUrl("test-state");
        assertTrue(url.startsWith("https://oauth-login.cloud.huawei.com/oauth2/v3/authorize"));
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fcodeup.asia%2Fapi%2Fauth%2Foauth%2Fhuawei%2Fcallback")
                || url.contains("redirect_uri=https://codeup.asia/api/auth/oauth/huawei/callback"));
        assertTrue(url.contains("scope=openid%20profile"));
        assertTrue(url.contains("state=test-state"));
    }
}
