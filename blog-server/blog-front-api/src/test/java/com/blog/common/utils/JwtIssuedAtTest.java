package com.blog.common.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * token 里的毫秒签发时刻。
 *
 * <p>为什么值得单独测：撤销机制靠它区分「撤销前签的」和「撤销后重新登的」。
 * 若哪天有人把它退回标准的 {@code iat}（数字秒），同一秒内撤销 + 重新登录就会
 * 互相误伤 —— 那种 bug 只在秒边界上偶发，线上极难复现。
 */
class JwtIssuedAtTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "expiration", 86_400_000L);
    }

    @Test
    void issuedAtIsMillisecondPrecise() {
        long before = System.currentTimeMillis();
        String token = jwtUtils.generateToken(1L, "u", "user");
        long issuedAt = jwtUtils.getIssuedAtMillis(token);
        long after = System.currentTimeMillis();

        // 毫秒精度下必然落在请求前后之间；退回秒精度会向下取整到秒起点，
        // 于是 issuedAt 可能比 before 小最多 999ms —— 这个断言正好拦住那种回退
        assertTrue(issuedAt >= before && issuedAt <= after,
                "签发时刻必须是毫秒级，实际 " + issuedAt + " 不在 [" + before + ", " + after + "] 内");
    }

    @Test
    void legacyTokenWithoutTsClaim_fallsBackToIatSeconds() {
        // 造一个本次改动之前签发的 token：只有标准的 iat（秒），没有自定义 ts
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String legacy = Jwts.builder()
                .subject("2")
                .claim("role", "user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        long issuedAt = jwtUtils.getIssuedAtMillis(legacy);

        assertEquals(0, issuedAt % 1000,
                "没有 ts 的老 token 只能退回 iat（秒精度）—— 兼容期必须能解析，不能抛异常");
    }
}
