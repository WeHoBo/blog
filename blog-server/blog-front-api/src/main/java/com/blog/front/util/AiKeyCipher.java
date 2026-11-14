package com.blog.front.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 模型密钥的对称加密（AES-256-GCM）。
 *
 * <p>为什么不直接存明文：数据库备份、慢查询日志、误提交的 dump 都可能把明文带出去。
 * 密文只在"后台保存"和"RAG 拉配置"两个瞬间在内存里还原。
 *
 * <p>格式 {@code iv:tag:cipher}（三段均为 Base64）——
 * 分隔存储是为了将来能单独轮换 iv 长度或 tag 长度，不至于整表重加密。
 *
 * <p>⚠️ 主密钥来自 {@code ai.key-secret}，**一旦更换，已入库的密钥全部无法解密**，
 * 后台只能重新填一遍。所以它必须和 JWT_SECRET 一样按"长期不变"对待。
 */
public final class AiKeyCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;      // GCM 推荐 96 bit
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AiKeyCipher() {
    }

    /** 是否具备加密能力。未配置主密钥时必须拒绝保存密钥，而不是退化成明文存储。 */
    public static boolean available(String secret) {
        return secret != null && !secret.isBlank();
    }

    public static String encrypt(String secret, String plain) {
        if (!available(secret)) {
            throw new IllegalStateException("未配置 ai.key-secret，无法加密密钥");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keyOf(secret), new GCMParameterSpec(TAG_BITS, iv));
            byte[] out = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            // JCE 把 tag 拼在密文尾部，这里显式拆开以匹配 iv:tag:cipher 格式
            int tagLen = TAG_BITS / 8;
            byte[] body = new byte[out.length - tagLen];
            byte[] tag = new byte[tagLen];
            System.arraycopy(out, 0, body, 0, body.length);
            System.arraycopy(out, body.length, tag, 0, tagLen);

            Base64.Encoder b64 = Base64.getEncoder();
            return b64.encodeToString(iv) + ":" + b64.encodeToString(tag) + ":" + b64.encodeToString(body);
        } catch (Exception e) {
            throw new IllegalStateException("密钥加密失败: " + e.getMessage(), e);
        }
    }

    /** 解密失败返回 null（主密钥换了、或数据被改过），由调用方决定是否降级 */
    public static String decrypt(String secret, String stored) {
        if (!available(secret) || stored == null || stored.isBlank()) {
            return null;
        }
        try {
            String[] parts = stored.split(":");
            if (parts.length != 3) {
                return null;
            }
            Base64.Decoder b64 = Base64.getDecoder();
            byte[] iv = b64.decode(parts[0]);
            byte[] tag = b64.decode(parts[1]);
            byte[] body = b64.decode(parts[2]);

            byte[] joined = new byte[body.length + tag.length];
            System.arraycopy(body, 0, joined, 0, body.length);
            System.arraycopy(tag, 0, joined, body.length, tag.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyOf(secret), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(joined), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 回显用掩码：只露出头 3 位和尾 4 位。
     *
     * <p>不足以反推密钥，但足够让管理员确认"填的是哪一把"（比如区分测试 key 和正式 key）。
     */
    public static String mask(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        String s = plain.trim();
        if (s.length() <= 8) {
            return "****";
        }
        return s.substring(0, Math.min(3, s.length())) + "****" + s.substring(s.length() - 4);
    }

    /**
     * 由任意长度口令派生 32 字节密钥。
     * 直接用 SHA-256 而不是 PBKDF2：这是服务端配置项，不是用户口令，不需要抗暴力破解的慢哈希。
     */
    private static SecretKeySpec keyOf(String secret) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
