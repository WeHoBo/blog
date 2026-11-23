package com.blog.front.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.dto.LoginDTO;
import com.blog.common.dto.PasswordChangeDTO;
import com.blog.common.dto.PasswordResetDTO;
import com.blog.common.dto.PhoneBindDTO;
import com.blog.common.dto.RegisterDTO;
import com.blog.common.dto.SmsLoginDTO;
import com.blog.common.entity.User;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.UserMapper;
import com.blog.common.security.TokenRevocationService;
import com.blog.common.utils.JwtUtils;
import com.blog.common.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 账号体系：注册、两种登录、找回密码、设置密码、绑定手机。
 *
 * <p><b>三条安全纪律</b>：
 * <ol>
 *   <li>密码登录失败 5 次锁 15 分钟 —— 不锁的话 6 位以上密码在线爆破是可行的，
 *       而且博客的用户名通常在评论区就能看到。</li>
 *   <li>登录失败的提示统一为"用户名或密码错误"，不区分"用户不存在/密码错"，
 *       避免被用来枚举站内有哪些账号。</li>
 *   <li>改密码分两种：未登录走短信验证码重置；已登录且没设过密码的可以直接设，
 *       已设过密码的必须提供旧密码 —— 否则拿到一台已登录的设备就能直接改密码。</li>
 * </ol>
 *
 * <p><b>关于 OAuth 老账号</b>：他们的 {@code password} 是 BCrypt(随机 UUID)，
 * {@code passwordSet=0}。这类账号走密码登录时会被明确告知"还没设置密码"，
 * 而不是让他对着一个自己不知道的密码反复试错。短信登录不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    /** 密码允许连续错几次 */
    private static final int MAX_LOGIN_FAIL = 5;
    /** 锁定多久 */
    private static final Duration LOGIN_LOCK = Duration.ofMinutes(15);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final SmsCodeService smsCodeService;
    private final StringRedisTemplate redis;
    private final TokenRevocationService revocationService;

    // ============================ 注册 ============================

    /**
     * 注册并直接登录。
     *
     * <p>先校验验证码再查库：无效请求不该打到数据库，也避免被用来枚举用户名。
     */
    @Transactional
    public LoginVO register(RegisterDTO dto) {
        String phone = dto.getPhone();
        String username = dto.getUsername().trim();

        smsCodeService.verify(phone, SmsCodeService.Scene.REGISTER, dto.getCode());

        if (countByUsername(username) > 0) {
            throw new BusinessException(400, "该用户名已被占用，换一个试试");
        }
        if (countByPhone(phone) > 0) {
            throw new BusinessException(400, "该手机号已注册，请直接登录");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPasswordSet(1);
        user.setNickname(StrUtil.isBlank(dto.getNickname()) ? username : dto.getNickname().trim());
        user.setPhone(phone);
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setRole("user");
        user.setStatus(1);
        user.setSource("local");

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 上面的 count 检查在并发下会漏（两个请求同时通过检查），唯一索引是最后一道闸
            log.warn("注册撞唯一索引 username={} phone={}", username, SmsService.mask(phone));
            throw new BusinessException(400, "用户名或手机号已被占用");
        }
        log.info("新用户注册 uid={} username={} phone={}", user.getId(), username, SmsService.mask(phone));
        return issue(user);
    }

    // ============================ 密码登录 ============================

    public LoginVO loginByPassword(LoginDTO dto) {
        String username = dto.getUsername().trim();
        String failKey = "login:fail:" + username.toLowerCase();

        long fails = readFail(failKey);
        if (fails >= MAX_LOGIN_FAIL) {
            throw new BusinessException(429, "密码错误次数过多，请 15 分钟后再试");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (user == null) {
            // 用户不存在也计数：否则攻击者能靠"是否被锁定"反推出该用户名不存在
            bumpFail(failKey);
            throw new BusinessException(400, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系站长");
        }
        if (user.getPasswordSet() == null || user.getPasswordSet() != 1) {
            // 这类是 OAuth 自动建号：password 有值但没人知道原文。
            // 明确告诉他下一步该怎么做，比让他瞎试密码有用得多。
            throw new BusinessException(400,
                    "该账号还没有设置密码。请用手机验证码登录，或先用第三方账号登录后在「个人中心 → 账号安全」里设置密码");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            long now = bumpFail(failKey);
            long left = Math.max(0, MAX_LOGIN_FAIL - now);
            throw new BusinessException(400,
                    left > 0 ? "用户名或密码错误，还可以再试 " + left + " 次" : "密码错误次数过多，请 15 分钟后再试");
        }

        clearFail(failKey);
        return issue(user);
    }

    // ============================ 验证码登录 ============================

    /**
     * 手机号 + 验证码登录。
     *
     * <p>故意<b>不</b>做"未注册自动建号"：注册入口是独立且显式的，
     * 两个入口都能建号会让用户名冲突处理变得难以解释，
     * 也会绕开"注册时设置密码"这一步。
     */
    public LoginVO loginBySms(SmsLoginDTO dto) {
        smsCodeService.verify(dto.getPhone(), SmsCodeService.Scene.LOGIN, dto.getCode());

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            throw new BusinessException(400, "该手机号尚未注册，请先注册");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系站长");
        }
        return issue(user);
    }

    // ============================ 密码管理 ============================

    /**
     * 忘记密码：手机号 + 验证码 + 新密码。
     *
     * <p>刻意不返回 token —— 让用户重新登录一次，
     * 这样"密码被改"这件事一定是用户自己下一步动作的结果，多一次确认。
     */
    public void resetPassword(PasswordResetDTO dto) {
        smsCodeService.verify(dto.getPhone(), SmsCodeService.Scene.RESET, dto.getCode());

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            throw new BusinessException(400, "该手机号尚未注册");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setPasswordSet(1);
        userMapper.updateById(user);
        // 密码变了，之前签发的 token 一律作废 —— 改密码的常见动机就是「怀疑被人登了」，
        // 不撤销的话那些会话还能继续用满 24 小时，等于改了也白改
        revocationService.revokeAll(user.getId());
        log.info("密码已重置 uid={} phone={}", user.getId(), SmsService.mask(dto.getPhone()));
    }

    /**
     * 已登录状态设置 / 修改密码。
     *
     * <p>已设过密码的必须验旧密码；从未设过的（OAuth 老账号）可以直接设 ——
     * 那批用户不存在"旧密码"这个概念。
     */
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        User user = requireUser(userId);

        boolean hasPassword = user.getPasswordSet() != null && user.getPasswordSet() == 1;
        if (hasPassword) {
            if (StrUtil.isBlank(dto.getOldPassword())) {
                throw new BusinessException(400, "请输入原密码");
            }
            if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
                throw new BusinessException(400, "原密码不正确");
            }
            if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
                throw new BusinessException(400, "新密码不能与原密码相同");
            }
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setPasswordSet(1);
        userMapper.updateById(user);
        if (hasPassword) {
            // 只有「改密码」才撤销：常见动机是怀疑密码泄露，旧会话必须立刻失效。
            // 「首次设置密码」只是补上一种登录方式，不存在"旧会话可能被别人持有"的问题，
            // 没必要把人踢出去。代价是**当前会话也会失效**，所以前端改成功后要主动退出。
            revocationService.revokeAll(userId);
        }
        log.info("密码已更新 uid={}", userId);
    }

    // ============================ 手机号 ============================

    /**
     * 绑定 / 更换手机号。
     *
     * <p>手机号是唯一索引，被别人占用时必须明确拒绝 ——
     * 否则两个账号指向同一号码后，"忘记密码"会把验证码发给同一个人，
     * 等于一个号码能接管两个账号。
     */
    public void bindPhone(Long userId, PhoneBindDTO dto) {
        smsCodeService.verify(dto.getPhone(), SmsCodeService.Scene.BIND, dto.getCode());

        User other = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (other != null && !other.getId().equals(userId)) {
            throw new BusinessException(400, "该手机号已被其他账号绑定");
        }

        User user = requireUser(userId);
        user.setPhone(dto.getPhone());
        user.setPhoneVerifiedAt(LocalDateTime.now());
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "该手机号已被其他账号绑定");
        }
        log.info("手机号已绑定 uid={} phone={}", userId, SmsService.mask(dto.getPhone()));
    }

    // ============================ 发码前置检查 ============================

    /**
     * 发验证码之前先判断「这个号码在这条流程里合理吗」。
     *
     * <p>目的有三个：① 用户输错号码时立刻提示，而不是白花一条短信；
     * ② 已注册的人来点"注册"、没注册的人来点"找回密码"，都能被拦在发短信之前；
     * ③ 减少可被利用的无效发送量 —— 短信是收费的。
     *
     * <p>代价是会暴露"某个号码是否已注册"。考虑到注册接口本身就会给出
     * 同样的提示，这个信息本来就无法隐藏，换来的省钱和体验更值。
     */
    public void precheckSmsScene(String phone, SmsCodeService.Scene scene, Long userId) {
        switch (scene) {
            case REGISTER -> {
                if (countByPhone(phone) > 0) {
                    throw new BusinessException(400, "该手机号已注册，请直接登录");
                }
            }
            case LOGIN, RESET -> {
                if (countByPhone(phone) == 0) {
                    throw new BusinessException(400, "该手机号尚未注册，请先注册");
                }
            }
            case BIND -> {
                if (userId == null) {
                    throw new BusinessException(401, "请先登录");
                }
                User other = userMapper.selectOne(
                        new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
                if (other != null && !other.getId().equals(userId)) {
                    throw new BusinessException(400, "该手机号已被其他账号绑定");
                }
            }
        }
    }

    // ============================ 内部工具 ============================

    private LoginVO issue(User user) {
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        return LoginVO.of(user, token);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(400, "用户不存在");
        }
        return user;
    }

    private long countByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private long countByPhone(String phone) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
    }

    /** 读失败计数。Redis 抖动时 fail-open（返回 0 = 没失败过），不能因为缓存故障锁死登录 */
    private long readFail(String key) {
        try {
            String raw = redis.opsForValue().get(key);
            return raw == null ? 0 : Long.parseLong(raw);
        } catch (Exception e) {
            log.warn("读取登录失败计数失败（fail-open）: {}", e.getMessage());
            return 0;
        }
    }

    private long bumpFail(String key) {
        try {
            Long v = redis.opsForValue().increment(key);
            if (v == null) {
                return 1;
            }
            if (v == 1L) {
                redis.expire(key, LOGIN_LOCK);
            }
            return v;
        } catch (Exception e) {
            log.warn("登录失败计数写入失败（忽略）: {}", e.getMessage());
            return 1;
        }
    }

    private void clearFail(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("清除登录失败计数失败（忽略）: {}", e.getMessage());
        }
    }
}
