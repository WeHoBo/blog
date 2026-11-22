package com.blog.front.service;

import com.blog.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信验证码的四道限流与校验逻辑。
 *
 * <p>这套东西的价值全在"被刷的时候"才体现，平时看不出区别 ——
 * 所以只能用测试把阈值钉死，避免哪天有人顺手改大或删掉一条防线。
 */
class SmsCodeServiceTest {

    private static final String PHONE = "13800138000";
    private static final String IP = "203.0.113.9";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private SmsService smsService;
    private SmsCodeService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        smsService = mock(SmsService.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(smsService.isEnabled()).thenReturn(true);
        when(smsService.getValidMinutes()).thenReturn(5);

        service = new SmsCodeService(redis, smsService);
    }

    // ==================== 四道限流 ====================

    @Test
    void send_withinCooldown_isRejectedWithRemainingSeconds() {
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(42L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.send(PHONE, SmsCodeService.Scene.LOGIN, IP));

        assertTrue(e.getMessage().contains("42"), e.getMessage());
        // 冷却期内绝不能真的发出去
        verify(smsService, never()).sendCode(anyString(), anyString());
    }

    @Test
    void send_phoneDailyLimitReached_isRejected() {
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-2L);
        when(valueOps.get(argThat((String k) -> k != null && k.startsWith("sms:day:phone"))))
                .thenReturn("5");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.send(PHONE, SmsCodeService.Scene.LOGIN, IP));

        assertTrue(e.getMessage().contains("今日"), e.getMessage());
        verify(smsService, never()).sendCode(anyString(), anyString());
    }

    @Test
    void send_ipDailyLimitReached_isRejected() {
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-2L);
        when(valueOps.get(argThat((String k) -> k != null && k.startsWith("sms:day:phone"))))
                .thenReturn("0");
        when(valueOps.get(argThat((String k) -> k != null && k.startsWith("sms:day:ip"))))
                .thenReturn("10");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.send(PHONE, SmsCodeService.Scene.LOGIN, IP));

        // 换号刷也挡得住：这一层按来源 IP 计
        assertTrue(e.getMessage().contains("网络"), e.getMessage());
    }

    @Test
    void send_globalDailyCapReached_circuitBreaks() {
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-2L);
        when(valueOps.get(argThat((String k) -> k != null && k.startsWith("sms:day:phone"))))
                .thenReturn("0");
        when(valueOps.get(argThat((String k) -> k != null && k.startsWith("sms:day:ip"))))
                .thenReturn("0");
        when(valueOps.get(argThat((String k) -> k != null && k.startsWith("sms:day:global"))))
                .thenReturn("200");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.send(PHONE, SmsCodeService.Scene.LOGIN, IP));

        // 全局熔断是最后一道：被刷时把一天的损失锁在 200 条以内
        assertTrue(e.getMessage().contains("上限"), e.getMessage());
        verify(smsService, never()).sendCode(anyString(), anyString());
    }

    @Test
    void send_success_storesCodeOnlyAfterSmsSent() {
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-2L);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);

        service.send(PHONE, SmsCodeService.Scene.REGISTER, IP);

        verify(smsService).sendCode(eq(PHONE), anyString());
        // 验证码必须写进 Redis，且带 TTL
        verify(valueOps).set(eq("sms:code:register:" + PHONE), anyString(), any(Duration.class));
    }

    @Test
    void send_smsFailure_doesNotWriteCodeToRedis() {
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-2L);
        when(valueOps.get(anyString())).thenReturn(null);
        org.mockito.Mockito.doThrow(new BusinessException(500, "发送失败"))
                .when(smsService).sendCode(anyString(), anyString());

        assertThrows(BusinessException.class,
                () -> service.send(PHONE, SmsCodeService.Scene.LOGIN, IP));

        // 顺序必须是「发成功 → 写缓存」。反过来的话，用户收不到短信却一直提示验证码错误
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ==================== 校验 ====================

    @Test
    void verify_expiredCode_isRejected() {
        when(valueOps.get(anyString())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verify(PHONE, SmsCodeService.Scene.LOGIN, "123456"));

        assertTrue(e.getMessage().contains("已过期"), e.getMessage());
    }

    @Test
    void verify_wrongCode_countsFailureAndReportsRemaining() {
        when(valueOps.get(anyString())).thenReturn("123456");
        when(valueOps.increment(anyString())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verify(PHONE, SmsCodeService.Scene.LOGIN, "000000"));

        assertTrue(e.getMessage().contains("还可以再试 4 次"), e.getMessage());
    }

    @Test
    void verify_tooManyFailures_invalidatesCode() {
        when(valueOps.get(anyString())).thenReturn("123456");
        when(valueOps.increment(anyString())).thenReturn(5L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verify(PHONE, SmsCodeService.Scene.LOGIN, "000000"));

        assertTrue(e.getMessage().contains("次数过多"), e.getMessage());
        // 达到上限必须销毁验证码，逼用户重新获取（也就是重新走一遍限流）
        verify(redis, org.mockito.Mockito.atLeastOnce()).delete(anyString());
    }

    @Test
    void verify_correctCode_consumesIt() {
        when(valueOps.get(anyString())).thenReturn("123456");

        assertDoesNotThrow(() -> service.verify(PHONE, SmsCodeService.Scene.LOGIN, "123456"));

        // 一次性：用过即删，防重放
        verify(redis, org.mockito.Mockito.atLeastOnce()).delete(anyString());
    }

    @Test
    void verify_malformedCode_isRejectedWithoutTouchingRedis() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verify(PHONE, SmsCodeService.Scene.LOGIN, "12ab56"));

        assertTrue(e.getMessage().contains("格式"), e.getMessage());
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void sceneKeysAreIsolated() {
        // 同一个手机号，登录的验证码不能拿去重置密码
        when(valueOps.get("sms:code:reset:" + PHONE)).thenReturn("111111");
        when(valueOps.get("sms:code:login:" + PHONE)).thenReturn(null);

        assertDoesNotThrow(() -> service.verify(PHONE, SmsCodeService.Scene.RESET, "111111"));
        assertThrows(BusinessException.class,
                () -> service.verify(PHONE, SmsCodeService.Scene.LOGIN, "111111"));
    }
}
