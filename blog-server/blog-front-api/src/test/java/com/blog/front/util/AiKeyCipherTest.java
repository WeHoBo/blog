package com.blog.front.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密钥加密的边界测试。
 *
 * 这块出错没有"报错"，只有"密钥悄悄存成了明文"或"换主密钥后全部解不开"，
 * 所以每条都要锁住。
 */
class AiKeyCipherTest {

    private static final String SECRET = "test-master-secret-please-change";

    /** 加解密往返 */
    @Test
    void roundTrip() {
        String plain = "sk-abcdef1234567890abcdef1234567890";
        String stored = AiKeyCipher.encrypt(SECRET, plain);

        assertEquals(plain, AiKeyCipher.decrypt(SECRET, stored));
    }

    /** 同一明文两次加密必须得到不同密文（iv 随机），否则等于泄漏"这两行的 key 一样" */
    @Test
    void sameInputProducesDifferentCipher() {
        String a = AiKeyCipher.encrypt(SECRET, "sk-same");
        String b = AiKeyCipher.encrypt(SECRET, "sk-same");

        assertNotEquals(a, b);
        assertEquals("sk-same", AiKeyCipher.decrypt(SECRET, a));
        assertEquals("sk-same", AiKeyCipher.decrypt(SECRET, b));
    }

    /** 密文格式必须是 iv:tag:cipher 三段 */
    @Test
    void cipherFormatHasThreeParts() {
        String stored = AiKeyCipher.encrypt(SECRET, "sk-x");
        assertEquals(3, stored.split(":").length);
    }

    /** 换了主密钥 → 解不开，返回 null（调用方据此告警，而不是拿错 key 去请求） */
    @Test
    void wrongSecretReturnsNull() {
        String stored = AiKeyCipher.encrypt(SECRET, "sk-x");
        assertNull(AiKeyCipher.decrypt("another-secret", stored));
    }

    /** 密文被篡改 → GCM 校验失败，返回 null 而不是垃圾数据 */
    @Test
    void tamperedCipherReturnsNull() {
        String stored = AiKeyCipher.encrypt(SECRET, "sk-x");
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";
        assertNull(AiKeyCipher.decrypt(SECRET, tampered));
    }

    /** 未配置主密钥时**必须**拒绝加密，绝不允许退化成明文存储 */
    @Test
    void encryptWithoutSecretThrows() {
        assertThrows(IllegalStateException.class, () -> AiKeyCipher.encrypt("", "sk-x"));
        assertThrows(IllegalStateException.class, () -> AiKeyCipher.encrypt(null, "sk-x"));
    }

    @Test
    void availableReflectsSecret() {
        assertTrue(AiKeyCipher.available(SECRET));
        assertFalse(AiKeyCipher.available("  "));
        assertFalse(AiKeyCipher.available(null));
    }

    /** 掩码只留头 3 尾 4，用于"确认填的是哪一把"，不足以反推 */
    @Test
    void maskKeepsHeadAndTailOnly() {
        String masked = AiKeyCipher.mask("sk-1234567890abcdef");
        assertTrue(masked.startsWith("sk-"));
        assertTrue(masked.endsWith("cdef"));
        assertTrue(masked.contains("****"));
        assertTrue(masked.length() < "sk-1234567890abcdef".length());
    }

    /** 太短的密钥全遮，避免掩码本身泄漏完整内容 */
    @Test
    void shortKeyFullyMasked() {
        assertEquals("****", AiKeyCipher.mask("abc"));
        assertEquals("", AiKeyCipher.mask(null));
    }
}
