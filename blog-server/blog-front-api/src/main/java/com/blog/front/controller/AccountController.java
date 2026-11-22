package com.blog.front.controller;

import com.blog.common.dto.LoginDTO;
import com.blog.common.dto.PasswordChangeDTO;
import com.blog.common.dto.PasswordResetDTO;
import com.blog.common.dto.PhoneBindDTO;
import com.blog.common.dto.RegisterDTO;
import com.blog.common.dto.Result;
import com.blog.common.dto.SmsLoginDTO;
import com.blog.common.dto.SmsSendDTO;
import com.blog.common.exception.BusinessException;
import com.blog.common.vo.LoginVO;
import com.blog.front.config.ApiAccessLogFilter;
import com.blog.front.service.AccountService;
import com.blog.front.service.SmsCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 账号相关接口：注册、两种登录、找回密码、设置密码、绑定手机。
 *
 * <p><b>🔴 这里每个匿名接口都必须在 {@code SecurityConfig} 里显式 permitAll</b>。
 * 项目兜底是 {@code anyRequest().permitAll()}，但 {@code /api/**} 的
 * {@code authenticated()} 排在它前面，漏写就是没登录永远 403 ——
 * 而且名字看起来"已经放行了"，最难查。
 *
 * <p>匿名可访问的只有：发验证码、注册、两种登录、找回密码。
 * 改密码和绑手机必须登录 —— 它们是"我已经是这个账号的主人"才能做的动作。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final SmsCodeService smsCodeService;

    /**
     * 发送短信验证码。
     *
     * <p>匿名可调，所以四道限流全压在 {@link SmsCodeService} 里：
     * 同号 60 秒冷却 + 日 5 条、同 IP 日 10 条、全局日 200 条熔断。
     * 没有这些，这个接口等于一张可以被脚本随便刷的支票。
     */
    @PostMapping("/sms/send")
    public Result<?> sendSms(@Valid @RequestBody SmsSendDTO dto, HttpServletRequest request) {
        SmsCodeService.Scene scene = parseScene(dto.getScene());
        accountService.precheckSmsScene(dto.getPhone(), scene, currentUserId());
        smsCodeService.send(dto.getPhone(), scene, ApiAccessLogFilter.clientIp(request));
        return Result.ok();
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(accountService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(accountService.loginByPassword(dto));
    }

    @PostMapping("/login/sms")
    public Result<LoginVO> loginBySms(@Valid @RequestBody SmsLoginDTO dto) {
        return Result.ok(accountService.loginBySms(dto));
    }

    /** 忘记密码。成功不返回 token，让用户重新登录一次 */
    @PostMapping("/password/reset")
    public Result<?> resetPassword(@Valid @RequestBody PasswordResetDTO dto) {
        accountService.resetPassword(dto);
        return Result.ok();
    }

    /** 已登录状态设置 / 修改密码 */
    @PutMapping("/password")
    public Result<?> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        accountService.changePassword(requireLogin(), dto);
        return Result.ok();
    }

    /** 已登录状态绑定 / 更换手机号 */
    @PostMapping("/phone/bind")
    public Result<?> bindPhone(@Valid @RequestBody PhoneBindDTO dto) {
        accountService.bindPhone(requireLogin(), dto);
        return Result.ok();
    }

    // ---------------- 工具 ----------------

    /** 白名单转换：不让前端随意字符串在 Redis 里造出新的 key 前缀 */
    private SmsCodeService.Scene parseScene(String raw) {
        if (raw == null) {
            throw new BusinessException(400, "缺少验证码用途");
        }
        return switch (raw.trim().toLowerCase()) {
            case "register" -> SmsCodeService.Scene.REGISTER;
            case "login" -> SmsCodeService.Scene.LOGIN;
            case "reset" -> SmsCodeService.Scene.RESET;
            case "bind" -> SmsCodeService.Scene.BIND;
            default -> throw new BusinessException(400, "不支持的验证码用途");
        };
    }

    /**
     * 当前登录用户 id，未登录返回 null。
     *
     * <p>不能直接 {@code getAuthentication().getPrincipal()}：这个接口是 permitAll，
     * 没带 token 时 authentication 是 null 或 anonymous，强转会抛 NPE。
     */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            return null;
        }
        return userId;
    }

    private Long requireLogin() {
        Long userId = currentUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}
