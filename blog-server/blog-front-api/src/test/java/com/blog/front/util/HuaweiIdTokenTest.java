package com.blog.front.util;

import com.blog.common.dto.OAuthUser;
import com.blog.common.oauth.HuaweiOAuthProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HuaweiIdTokenTest {

    @Test
    void decodeIdToken_extractsUserInfo() throws Exception {
        String payload = "{\"openID\":\"openid-abc-1234\",\"displayName\":\"张三\",\"headPictureURL\":\"https://img.example/a.png\",\"email\":\"z@example.com\"}";
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String idToken = "header." + encodedPayload + ".signature";

        HuaweiOAuthProvider provider = new HuaweiOAuthProvider();
        java.lang.reflect.Method m = HuaweiOAuthProvider.class.getDeclaredMethod("parseUserFromIdToken", String.class);
        m.setAccessible(true);
        OAuthUser user = (OAuthUser) m.invoke(provider, idToken);

        assertNotNull(user);
        assertEquals("openid-abc-1234", user.getOpenId());
        assertEquals("张三", user.getNickname());
        assertEquals("https://img.example/a.png", user.getAvatar());
        assertEquals("z@example.com", user.getEmail());
    }

    @Test
    void decodeIdToken_fallbackNickname() throws Exception {
        String payload = "{\"openID\":\"openid-xyz-5678\"}";
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String idToken = "h." + encodedPayload + ".s";

        HuaweiOAuthProvider provider = new HuaweiOAuthProvider();
        java.lang.reflect.Method m = HuaweiOAuthProvider.class.getDeclaredMethod("parseUserFromIdToken", String.class);
        m.setAccessible(true);
        OAuthUser user = (OAuthUser) m.invoke(provider, idToken);

        assertNotNull(user);
        assertEquals("openid-xyz-5678", user.getOpenId());
        assertEquals("华为用户_5678", user.getNickname());
    }
}
