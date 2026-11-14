package com.blog.common.security;

import cn.hutool.core.util.StrUtil;
import com.blog.common.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;

    /**
     * StreamingResponseBody 等异步请求在收尾阶段会触发 ASYNC dispatch，
     * Security 的 AuthorizationFilter 会再次鉴权；若这里跳过异步分发，
     * 上下文为空会被拒绝（Access Denied）并掐断已提交的流式响应。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);
        if (StrUtil.isNotBlank(token) && jwtUtils.validate(token)) {
            Long userId = jwtUtils.getUserId(token);
            String role = (String) jwtUtils.parseToken(token).get("role");
            List<GrantedAuthority> authorities = authoritiesOf(role);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 把 JWT 里的 role 字符串翻译成 Spring Security 的权限列表。
     *
     * <p><b>超管继承管理员</b>：{@code super} 会同时拿到 {@code ROLE_admin} 和 {@code ROLE_super}。
     * 这是刻意的 —— 全站有 30 多处 {@code hasRole('admin')}，如果 super 只带 {@code ROLE_super}，
     * 那么超管连后台列表都打不开，且以后每加一个 admin 接口都要记得补 super，
     * 漏一处就是「超管权限反而比管理员小」的诡异 bug。反过来做「继承」，
     * 超管天然拥有管理员的全部能力，只有我们显式改成 {@code hasRole('super')} 的地方才收窄。
     *
     * <p>注意：这个是**单向**的，{@code admin} 永远拿不到 {@code ROLE_super}。
     */
    private List<GrantedAuthority> authoritiesOf(String role) {
        String r = StrUtil.blankToDefault(role, "user");
        if ("super".equals(r)) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_admin"),
                    new SimpleGrantedAuthority("ROLE_super"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + r));
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
