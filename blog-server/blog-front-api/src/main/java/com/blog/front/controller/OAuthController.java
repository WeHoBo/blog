package com.blog.front.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.dto.OAuthUser;
import com.blog.common.dto.Result;
import com.blog.common.entity.User;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.UserMapper;
import com.blog.common.oauth.GitHubOAuthProvider;
import com.blog.common.oauth.GiteeOAuthProvider;
import com.blog.common.oauth.HuaweiOAuthProvider;
import com.blog.common.oauth.OAuthProvider;
import com.blog.common.utils.JwtUtils;
import com.blog.common.vo.LoginVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    /** OAuth CSRF 防护：authorize 阶段下发 state，回调阶段一次性校验 */
    private static final String STATE_KEY_PREFIX = "oauth:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final GitHubOAuthProvider githubProvider;
    private final GiteeOAuthProvider giteeProvider;
    private final HuaweiOAuthProvider huaweiProvider;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.web-url:http://localhost:3000}")
    private String webUrl;

    private OAuthProvider getProvider(String provider) {
        return switch (provider) {
            case "github" -> githubProvider;
            case "gitee" -> giteeProvider;
            case "huawei" -> huaweiProvider;
            default -> throw new IllegalArgumentException("不支持的平台: " + provider);
        };
    }

    @GetMapping("/{provider}")
    public void authorize(@PathVariable String provider, HttpServletResponse response) throws IOException {
        OAuthProvider p = getProvider(provider);
        String state = IdUtil.simpleUUID();
        // 记录本次授权会话，回调时校验，防止 OAuth 登录 CSRF（攻击者诱导用户以其身份登录攻击者账号）
        stringRedisTemplate.opsForValue().set(STATE_KEY_PREFIX + state, provider, STATE_TTL);
        response.sendRedirect(p.getAuthorizeUrl(state));
    }

    @GetMapping("/{provider}/callback")
    public void callback(@PathVariable String provider,
                         @RequestParam(required = false) String code,
                         @RequestParam(required = false) String authorization_code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         @RequestParam(required = false) String error_description,
                         HttpServletResponse response) throws IOException {
        // 平台侧直接回了 error（例如用户点了「取消授权」），此时不会有 code
        if (StrUtil.isNotBlank(error)) {
            redirectError(response, "授权被拒绝或失败: " + error
                    + (StrUtil.isNotBlank(error_description) ? "（" + error_description + "）" : ""));
            return;
        }
        // 以下所有失败都重定向回前端并带上原因，而不是返回 JSON —— 用户才看得见为什么失败
        if (state == null || state.isBlank()) {
            redirectError(response, "缺少 state 参数，请重新发起登录");
            return;
        }
        String stateKey = STATE_KEY_PREFIX + state;
        String expectedProvider = stringRedisTemplate.opsForValue().get(stateKey);
        stringRedisTemplate.delete(stateKey);   // 一次性使用，无论校验是否通过都作废
        if (expectedProvider == null || !expectedProvider.equals(provider)) {
            redirectError(response, "登录会话已过期或无效，请重新发起登录");
            return;
        }

        // 华为回跳参数名为 authorization_code，GitHub/Gitee 为 code
        String authCode = code != null ? code : authorization_code;
        if (authCode == null) {
            redirectError(response, "缺少授权码参数，请重新发起登录");
            return;
        }

        OAuthProvider p = getProvider(provider);
        String accessToken;
        OAuthUser oauthUser;
        try {
            accessToken = p.getAccessToken(authCode);
            oauthUser = p.getUserInfo(accessToken);
        } catch (BusinessException e) {
            // Provider 抛出的业务异常带出了平台返回的 error / error_description，原样透传给用户
            redirectError(response, humanizeOAuthError(e.getMessage()));
            return;
        }

        String providerKey = provider + "_id";
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().apply(providerKey + " = {0}", oauthUser.getOpenId()));

        if (user == null) {
            user = new User();
            user.setUsername(oauthUser.getUsername());
            user.setPassword(passwordEncoder.encode(IdUtil.simpleUUID()));
            user.setNickname(oauthUser.getNickname());
            user.setAvatar(oauthUser.getAvatar());
            user.setEmail(oauthUser.getEmail());
            user.setSource(provider);
            user.setRole("user");
            user.setStatus(1);

            if ("github".equals(provider)) {
                user.setGithubId(oauthUser.getOpenId());
            } else if ("gitee".equals(provider)) {
                user.setGiteeId(oauthUser.getOpenId());
            } else if ("huawei".equals(provider)) {
                user.setHuaweiId(oauthUser.getOpenId());
            }
            userMapper.insert(user);
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        // 参数 URL 编码，防止中文昵称/特殊字符导致 Location header 非法
        String callbackUrl = webUrl + "/oauth/callback?token=" + token
                + "&userId=" + user.getId()
                + "&username=" + URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8)
                + "&nickname=" + URLEncoder.encode(user.getNickname() != null ? user.getNickname() : "", StandardCharsets.UTF_8)
                + "&avatar=" + URLEncoder.encode(user.getAvatar() != null ? user.getAvatar() : "", StandardCharsets.UTF_8)
                + "&role=" + user.getRole();
        response.sendRedirect(callbackUrl);
    }

    /**
     * 失败一律重定向回前端的 /oauth/callback 并带上 error 参数，由前端展示。
     * <p>
     * 以前这里是直接抛 BusinessException，用户侧只会看到一个转圈动画（callback.vue 只有 spinner），
     * 既不知道失败原因也无法自助解决 —— 排查 GitHub 登录问题时因此绕了很多弯路。
     * 现在把原因明确带到页面上，任何 OAuth 故障都能一眼定位。
     */
    private void redirectError(HttpServletResponse response, String message) throws IOException {
        String url = webUrl + "/oauth/callback?error="
                + URLEncoder.encode(message != null ? message : "登录失败，请稍后重试", StandardCharsets.UTF_8);
        response.sendRedirect(url);
    }

    /**
     * 把平台返回的英文技术错误翻译成用户能看懂的中文提示。
     * 未命中规则时原样返回，保证信息不丢失（便于排查）。
     */
    private String humanizeOAuthError(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "登录失败，请稍后重试";
        }
        String lower = raw.toLowerCase();
        if (lower.contains("redirect_uri_mismatch")) {
            return "第三方平台回调地址配置不匹配，站点配置有误，请联系站长";
        }
        if (lower.contains("bad_verification_code")) {
            return "授权码已失效（可能重复使用了回调地址或停留过久），请重新登录";
        }
        if (lower.contains("incorrect_client_credentials")) {
            return "第三方平台应用凭据无效，请联系站长检查配置";
        }
        if (lower.contains("unverified_email") || lower.contains("email")) {
            return "无法获取邮箱信息（可能是第三方账号未公开邮箱），请更换登录方式";
        }
        if (lower.contains("网络不通") || lower.contains("timeout") || lower.contains("timed out")) {
            return "连接第三方平台超时，请稍后重试";
        }
        return raw;
    }
}
