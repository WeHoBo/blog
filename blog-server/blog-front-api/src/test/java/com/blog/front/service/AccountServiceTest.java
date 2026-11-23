package com.blog.front.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号体系的核心不变量。
 *
 * <p>这里测的都是「错了会出安全问题」的点，不是覆盖率：
 * 老账号的密码提示、失败锁定、改密码要不要旧密码、手机号被占用要拒绝。
 */
class AccountServiceTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private SmsCodeService smsCodeService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private AccountService service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析 User::getUsername 需要 TableInfo，否则运行期报缓存缺失
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        JwtUtils jwtUtils = mock(JwtUtils.class);
        smsCodeService = mock(SmsCodeService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(jwtUtils.generateToken(any(), any(), any())).thenReturn("test-token");

        service = new AccountService(userMapper, passwordEncoder, jwtUtils, smsCodeService, redis,
                mock(TokenRevocationService.class));
    }

    // ==================== 注册 ====================

    @Test
    void register_duplicateUsername_isRejected() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.register(registerDTO()));

        assertTrue(e.getMessage().contains("用户名已被占用"), e.getMessage());
    }

    @Test
    void register_duplicatePhone_isRejected() {
        // 第一次查询（用户名）返回 0，第二次（手机号）返回 1
        when(userMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.register(registerDTO()));

        assertTrue(e.getMessage().contains("手机号已注册"), e.getMessage());
    }

    @Test
    void register_success_marksPasswordSetAndBindsPhone() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("bcrypt-hash");

        LoginVO vo = service.register(registerDTO());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();

        assertEquals("bcrypt-hash", saved.getPassword());
        // 这是本次改造的关键：注册时密码是用户自己设的，标记必须为 1
        assertEquals(1, saved.getPasswordSet());
        assertEquals("13800138000", saved.getPhone());
        assertEquals("local", saved.getSource());
        assertEquals("user", saved.getRole());
        // 手机号是验证码验过的，验证时间必须有值
        assertTrue(saved.getPhoneVerifiedAt() != null);

        assertEquals("test-token", vo.getToken());
        assertTrue(vo.getPasswordSet());
    }

    // ==================== 密码登录 ====================

    @Test
    void login_oauthAccountWithoutPassword_givesActionableHint() {
        // OAuth 自动建号：password 有值但 passwordSet=0
        User user = user(1L, "oauth-user");
        user.setPasswordSet(0);
        when(userMapper.selectOne(any())).thenReturn(user);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginByPassword(loginDTO("oauth-user", "whatever")));

        // 不能只说"用户名或密码错误"，否则这类用户永远猜不到原因
        assertTrue(e.getMessage().contains("还没有设置密码"), e.getMessage());
    }

    @Test
    void login_wrongPassword_reportsRemainingAttempts() {
        User user = user(1L, "alice");
        user.setPasswordSet(1);
        user.setPassword("hash");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(valueOps.increment(anyString())).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginByPassword(loginDTO("alice", "wrong")));

        assertTrue(e.getMessage().contains("还可以再试 3 次"), e.getMessage());
    }

    @Test
    void login_lockedOut_isRejectedBeforeTouchingDatabase() {
        when(valueOps.get(anyString())).thenReturn("5");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginByPassword(loginDTO("alice", "whatever")));

        assertTrue(e.getMessage().contains("次数过多"), e.getMessage());
        // 已锁定就不该再查库，省一次查询也避免被用来探测账号是否存在
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    void login_unknownUser_alsoCountsAsFailure() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginByPassword(loginDTO("nobody", "x")));

        assertEquals("用户名或密码错误", e.getMessage());
        // 账号不存在也要计数，否则"是否被锁"会暴露该用户名是否存在
        verify(valueOps).increment(anyString());
    }

    @Test
    void login_success_returnsTokenAndClearsFailCounter() {
        User user = user(7L, "alice");
        user.setPasswordSet(1);
        user.setPassword("hash");
        when(valueOps.get(anyString())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        LoginVO vo = service.loginByPassword(loginDTO("alice", "secret123"));

        assertEquals("test-token", vo.getToken());
        assertEquals("alice", vo.getUsername());
        verify(redis).delete(anyString());
    }

    @Test
    void login_disabledAccount_isRejected() {
        User user = user(1L, "banned");
        user.setStatus(0);
        when(valueOps.get(anyString())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(user);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginByPassword(loginDTO("banned", "x")));

        assertTrue(e.getMessage().contains("禁用"), e.getMessage());
    }

    // ==================== 短信登录 ====================

    @Test
    void loginBySms_unregisteredPhone_isRejected() {
        when(userMapper.selectOne(any())).thenReturn(null);

        SmsLoginDTO dto = new SmsLoginDTO();
        dto.setPhone("13900139000");
        dto.setCode("123456");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginBySms(dto));

        // 刻意不做"未注册自动建号"：注册入口是独立且显式的
        assertTrue(e.getMessage().contains("尚未注册"), e.getMessage());
    }

    @Test
    void loginBySms_success_returnsToken() {
        when(userMapper.selectOne(any())).thenReturn(user(9L, "bob"));

        SmsLoginDTO dto = new SmsLoginDTO();
        dto.setPhone("13900139000");
        dto.setCode("123456");

        LoginVO vo = service.loginBySms(dto);
        assertEquals("test-token", vo.getToken());
        verify(smsCodeService).verify("13900139000", SmsCodeService.Scene.LOGIN, "123456");
    }

    // ==================== 改密码 ====================

    @Test
    void changePassword_alreadySet_requiresOldPassword() {
        User user = user(1L, "alice");
        user.setPasswordSet(1);
        user.setPassword("hash");
        when(userMapper.selectById(1L)).thenReturn(user);

        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setNewPassword("newpass123");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.changePassword(1L, dto));

        assertTrue(e.getMessage().contains("请输入原密码"), e.getMessage());
    }

    @Test
    void changePassword_alreadySet_wrongOldPassword_isRejected() {
        User user = user(1L, "alice");
        user.setPasswordSet(1);
        user.setPassword("hash");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setOldPassword("bad");
        dto.setNewPassword("newpass123");

        assertThrows(BusinessException.class, () -> service.changePassword(1L, dto));
    }

    @Test
    void changePassword_neverSet_doesNotRequireOldPassword() {
        // OAuth 老账号：压根没有"旧密码"这个概念，逼他提供等于永久锁死
        User user = user(1L, "oauth-user");
        user.setPasswordSet(0);
        user.setPassword("random-uuid-hash");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.encode("newpass123")).thenReturn("new-hash");

        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setNewPassword("newpass123");

        assertDoesNotThrow(() -> service.changePassword(1L, dto));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getPasswordSet());
        assertEquals("new-hash", captor.getValue().getPassword());
    }

    // ==================== 绑定手机 ====================

    @Test
    void bindPhone_takenByAnotherAccount_isRejected() {
        User other = user(2L, "other");
        when(userMapper.selectOne(any())).thenReturn(other);

        PhoneBindDTO dto = new PhoneBindDTO();
        dto.setPhone("13700137000");
        dto.setCode("123456");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.bindPhone(1L, dto));

        assertTrue(e.getMessage().contains("已被其他账号绑定"), e.getMessage());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void bindPhone_sameAccount_isIdempotent() {
        User mine = user(1L, "alice");
        when(userMapper.selectOne(any())).thenReturn(mine);
        when(userMapper.selectById(1L)).thenReturn(mine);

        PhoneBindDTO dto = new PhoneBindDTO();
        dto.setPhone("13700137000");
        dto.setCode("123456");

        assertDoesNotThrow(() -> service.bindPhone(1L, dto));
        verify(userMapper).updateById(any(User.class));
    }

    // ==================== 重置密码 ====================

    @Test
    void resetPassword_setsPasswordAndPasswordSetFlag() {
        User user = user(3L, "carol");
        user.setPasswordSet(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.encode("brandnew123")).thenReturn("hash2");

        PasswordResetDTO dto = new PasswordResetDTO();
        dto.setPhone("13600136000");
        dto.setCode("123456");
        dto.setNewPassword("brandnew123");

        service.resetPassword(dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getPasswordSet());
        assertEquals("hash2", captor.getValue().getPassword());
        // 重置走的是 RESET 场景的验证码，不能与登录验证码混用
        verify(smsCodeService).verify("13600136000", SmsCodeService.Scene.RESET, "123456");
    }

    @Test
    void resetPassword_unregisteredPhone_isRejected() {
        when(userMapper.selectOne(any())).thenReturn(null);

        PasswordResetDTO dto = new PasswordResetDTO();
        dto.setPhone("13600136000");
        dto.setCode("123456");
        dto.setNewPassword("brandnew123");

        assertThrows(BusinessException.class, () -> service.resetPassword(dto));
        verify(userMapper, never()).updateById(any(User.class));
    }

    // ==================== 发码前置检查 ====================

    @Test
    void precheck_registerWithExistingPhone_isRejected() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.precheckSmsScene("13800138000", SmsCodeService.Scene.REGISTER, null));

        assertTrue(e.getMessage().contains("已注册"), e.getMessage());
    }

    @Test
    void precheck_bindWithoutLogin_isRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.precheckSmsScene("13800138000", SmsCodeService.Scene.BIND, null));

        assertTrue(e.getMessage().contains("请先登录"), e.getMessage());
    }

    // ==================== 工具 ====================

    private User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setStatus(1);
        u.setRole("user");
        u.setPasswordSet(0);
        u.setPassword("random-hash");
        return u;
    }

    private LoginDTO loginDTO(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private RegisterDTO registerDTO() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newbie");
        dto.setPassword("secret123");
        dto.setPhone("13800138000");
        dto.setCode("123456");
        return dto;
    }
}
