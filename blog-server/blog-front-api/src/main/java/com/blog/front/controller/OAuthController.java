package com.blog.front.controller;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.dto.OAuthUser;
import com.blog.common.dto.Result;
import com.blog.common.entity.User;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final GitHubOAuthProvider githubProvider;
    private final GiteeOAuthProvider giteeProvider;
    private final HuaweiOAuthProvider huaweiProvider;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

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
        response.sendRedirect(p.getAuthorizeUrl(state));
    }

    @GetMapping("/{provider}/callback")
    public void callback(@PathVariable String provider,
                         @RequestParam(required = false) String code,
                         @RequestParam(required = false) String authorization_code,
                         HttpServletResponse response) throws IOException {
        // 华为回跳参数名为 authorization_code，GitHub/Gitee 为 code
        String authCode = code != null ? code : authorization_code;
        if (authCode == null) {
            throw new IllegalArgumentException("缺少授权码参数");
        }
        OAuthProvider p = getProvider(provider);
        String accessToken = p.getAccessToken(authCode);
        OAuthUser oauthUser = p.getUserInfo(accessToken);

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
                + "&username=" + java.net.URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8)
                + "&nickname=" + java.net.URLEncoder.encode(user.getNickname() != null ? user.getNickname() : "", StandardCharsets.UTF_8)
                + "&avatar=" + java.net.URLEncoder.encode(user.getAvatar() != null ? user.getAvatar() : "", StandardCharsets.UTF_8)
                + "&role=" + user.getRole();
        response.sendRedirect(callbackUrl);
    }
}
