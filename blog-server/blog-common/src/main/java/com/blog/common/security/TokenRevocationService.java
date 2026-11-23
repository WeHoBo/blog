package com.blog.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 让已经签发出去的 JWT 能失效。
 *
 * <p><b>为什么需要这个</b>：JWT 是自包含的，服务端只验签名、不查库，所以
 * 「禁用账号 / 把管理员降成普通用户 / 用户改了密码」这些操作对已经发出去的 token
 * 完全无效 —— 攻击者拿着旧 token 最多还能用满 24 小时。这不是理论风险：
 * 站长踢掉一个刷评论的账号，对方刷新一次页面又是一条好汉。
 *
 * <p><b>为什么记「撤销时刻」而不是维护一个黑名单列表</b>：黑名单要存每个 token 的
 * jti，24 小时内的所有 token 都得留在 Redis 里（还得自己清理）。记「该用户在某时刻
 * 之前签发的 token 一律作废」只要一个 key 一人，天然滚动过期，比对也只是一次时间比较。
 *
 * <p><b>为什么用毫秒而不是 JWT 标准里的 {@code iat}</b>：{@code iat} 是数字秒
 * （RFC 7519 NumericDate），同一秒内「撤销」和「重新登录」无法区分 —— 撤销后立刻
 * 登回来的新 token 会被自己刚写的撤销戳误伤，用户表现为「刚登录就被踢」。
 * 所以签发时额外塞一个毫秒 claim（{@code ts}），比对精度到毫秒，这个窗口就消失了。
 *
 * <p><b>Redis 故障时放行</b>：宁可撤销延迟生效，也不能让 Redis 一挂全站登录一起废掉。
 * 缓存挂了本来就只能回源，但认证是硬依赖 —— 这里选可用性，故障走告警日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String KEY_PREFIX = "user:revoked-at:";

    /**
     * ⚠️ TTL 必须大于签名有效期（{@code jwt.expiration}，默认 24h）。
     * 若 key 先于 token 过期，被撤销的 token 会在 key 消失后「复活」——
     * 这是这套机制唯一会导致静默失效的地方，改有效期时务必同时改这里。
     */
    private static final Duration TTL = Duration.ofHours(25);

    private final StringRedisTemplate redis;

    /**
     * 撤销该用户此刻之前签发的所有 token。
     *
     * <p>调用时机：禁用账号、改角色（升权也要 —— 不撤销的话旧 token 里冻的还是旧角色，
     * 用户会以为「站长给我升了管理员但我还是打不开后台」）、改密码、删账号。
     */
    public void revokeAll(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + userId,
                    String.valueOf(System.currentTimeMillis()), TTL);
        } catch (Exception e) {
            // 不能因为撤销失败就让「禁用账号」这个操作整体失败：账号状态已经落库了，
            // 至少下次登录会被 status 拦住。这里只是让旧 token 多活一会儿。
            log.warn("[凭证撤销] 写入失败，该用户的旧 token 仍有效直到自然过期 userId={}: {}",
                    userId, e.toString());
        }
    }

    /**
     * 这个签发时刻的 token 是否已经被撤销。
     *
     * @param issuedAtMillis token 里的毫秒签发时刻（见 {@code JwtUtils#getIssuedAtMillis}）
     */
    public boolean isRevoked(Long userId, long issuedAtMillis) {
        if (userId == null) {
            return false;
        }
        String value;
        try {
            value = redis.opsForValue().get(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("[凭证撤销] 读取失败，本次按未撤销处理 userId={}: {}", userId, e.toString());
            return false;
        }
        if (value == null) {
            return false;
        }
        try {
            // 严格小于：撤销那一刻之后签发的 token 必须继续有效，
            // 否则「禁用 → 解禁 → 重新登录」会立刻被自己上一次的撤销戳踢掉
            return issuedAtMillis < Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("[凭证撤销] 撤销戳格式异常，按未撤销处理 userId={} value={}", userId, value);
            return false;
        }
    }
}
