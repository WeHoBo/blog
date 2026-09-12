package com.blog.common.utils;

import cn.hutool.core.date.DateUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    /**
     * 刻意不提供默认值：占位符解析不到会直接让应用启动失败（fail-fast），
     * 避免"生产忘了配密钥 → 悄悄用了源码里公开的兜底密钥 → 任何人都能伪造 admin token"。
     * 本地开发由 application.yml 里的 jwt.secret 提供；生产由 application-prod.yml 提供。
     */
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    /**
     * 启动即校验密钥强度：缺失 / 空白 / 短于 32 字节（HS256 要求 256 bit）一律拒绝启动。
     * 与其带着一个弱密钥跑起来，不如启动就报错、让人立刻发现。
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 未配置：请在 application-prod.yml 或环境变量 JWT_SECRET 中设置密钥"
                            + "（可用 `openssl rand -base64 48` 生成）");
        }
        int len = secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "jwt.secret 仅 " + len + " 字节，HS256 要求至少 32 字节；"
                            + "请用 `openssl rand -base64 48` 重新生成");
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(exp)
                .signWith(getKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public boolean isExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean validate(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
