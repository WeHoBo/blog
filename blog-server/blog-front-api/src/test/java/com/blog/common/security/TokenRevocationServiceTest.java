package com.blog.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 撤销机制。这里钉死三件事：撤销前的 token 必须失效、撤销后新签的必须有效、
 * Redis 故障时必须放行（不能因为缓存挂了把全站登录一起干掉）。
 */
class TokenRevocationServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new TokenRevocationService(redis);
    }

    /** 把 revokeAll 实际写进 Redis 的那个字符串捞出来 */
    private long capturedRevokedAt(Long userId) {
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("user:revoked-at:" + userId), valueCaptor.capture(), any(Duration.class));
        return Long.parseLong(valueCaptor.getValue());
    }

    @Test
    void tokenIssuedBeforeRevocation_isRejected() {
        service.revokeAll(7L);
        long revokedAt = capturedRevokedAt(7L);
        // revokeAll 只写不读，读路径得单独 stub —— 否则 isRevoked 拿到的永远是 null，
        // 断言会「因为压根没读到撤销戳」而通过，测了个寂寞
        when(valueOps.get("user:revoked-at:7")).thenReturn(String.valueOf(revokedAt));

        assertTrue(service.isRevoked(7L, revokedAt - 1),
                "撤销前一毫秒签发的 token 必须失效，否则「禁用账号」只是摆设");
    }

    @Test
    void tokenIssuedAfterRevocation_staysValid() {
        service.revokeAll(7L);
        long revokedAt = capturedRevokedAt(7L);
        when(valueOps.get("user:revoked-at:7")).thenReturn(String.valueOf(revokedAt));

        assertFalse(service.isRevoked(7L, revokedAt),
                "撤销同一时刻之后签发的 token 必须继续有效 —— 否则「禁用 → 解禁 → 重新登录」"
                        + "会被自己上一次写的撤销戳立刻踢掉");
        assertFalse(service.isRevoked(7L, revokedAt + 1));
    }

    @Test
    void userNeverRevoked_isNotRevoked() {
        when(valueOps.get("user:revoked-at:8")).thenReturn(null);

        assertFalse(service.isRevoked(8L, System.currentTimeMillis()));
    }

    @Test
    void redisFailure_failsOpen() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertFalse(service.isRevoked(9L, 1000L),
                "Redis 故障时按未撤销处理：宁可撤销延迟生效，也不能让全站登录一起废掉");
    }

    @Test
    void malformedValue_failsOpen() {
        when(valueOps.get("user:revoked-at:10")).thenReturn("not-a-number");

        assertFalse(service.isRevoked(10L, 1000L));
    }
}
