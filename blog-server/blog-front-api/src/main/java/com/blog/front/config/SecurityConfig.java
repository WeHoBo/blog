package com.blog.front.config;

import com.blog.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 管理端评论接口必须先于下面的 GET /api/comment/** permitAll 匹配，
                // 否则 admin 的 GET 会被放行（方法级 @PreAuthorize 虽然还在，但别依赖两层兜底）
                .requestMatchers("/api/comment/admin/**").hasRole("admin")
                .requestMatchers(HttpMethod.GET, "/api/article/**").permitAll()
                // 阅读数打点：页面 HTML 被 routeRules 缓存后，服务端渲染不再每次都打详情接口，
                // 改由浏览器在挂载后自己上报一次。它必须匿名可调，否则缓存命中页的阅读数就不涨了。
                .requestMatchers(HttpMethod.POST, "/api/article/*/view").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/category/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tag/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/comment/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/friend-link/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/site-config/public").permitAll()
                .requestMatchers("/api/auth/oauth/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/doc.html").permitAll()
                // RAG 服务没有 JWT，只能用共享令牌：这里放行，由 InternalAiController 自己校验 X-RAG-Token，
                // 且 Nginx 已对 /internal/ 做 deny all（公网不可达）。两层都不能少。
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
