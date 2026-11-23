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
    private final TokenRevocationService revocationService;

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
            // 签发之后被撤销过的 token 一律当成没登录：不抛异常、不设 Authentication，
            // 让它自然落到下面「需要登录的接口 → 401、公开接口 → 匿名」的常规路径。
            // 这里救的是「禁用账号 / 降权 / 改密码」之后旧 token 还能用满 24 小时的问题。
            if (!revocationService.isRevoked(userId, jwtUtils.getIssuedAtMillis(token))) {
                String role = (String) jwtUtils.parseToken(token).get("role");
                List<GrantedAuthority> authorities = authoritiesOf(role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 给访问日志用：访问日志过滤器在 Security 链的外层，等 chain.doFilter 返回时
                // SecurityContext 已被清空，直接读 Authentication 一定是 null。
                // 请求对象活到最后，所以把 userId 挂到 request attribute 上。
                // （blog-common 不依赖 front-api，故此处用字面量，与 ApiAccessLogFilter.UID_ATTR 保持一致）
                request.setAttribute("blog.uid", userId);
            }
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
     *
     * <p><b>{@code author} 恰恰相反，只拿 {@code ROLE_author}</b>（走下面的通用分支）。
     * 这是「投稿作者」角色：能提交文章、只能改自己的文章，其余全部收窄。
     * 🔴 绝不能为了让作者"能用后台"而给他补 {@code ROLE_admin} ——
     * 那样一来 37 个管理员接口会全部对他敞开，包括改站点设置和删别人的文章，
     * 整套审核制就形同虚设。
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
