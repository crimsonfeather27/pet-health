package com.pethealth.interceptor;

import com.pethealth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.Set;

/**
 * 解析登录态 Token，将 userId 注入 request attribute
 * <p>
 * Token 承载方式（#6）：优先从 HttpOnly Cookie {@link #COOKIE_NAME} 读取，
 * 兼容 Authorization: Bearer 头（便于 curl / 第三方调试）。
 * <p>
 * 认证策略：
 * 1. 读接口（GET）：可选认证，未登录放行（社区浏览公开）
 * 2. 写接口（POST/PUT/DELETE/PATCH）：必须携带有效 Token，否则返回 401
 *    （登录/注册/登出除外）
 * 3. 资源属主校验由各 Service 层配合 OwnershipGuard 完成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ID = "currentUserId";

    /** 承载登录态的 HttpOnly Cookie 名 */
    public static final String COOKIE_NAME = "PETHEALTH_TOKEN";

    /** Cookie 有效期（秒），与 Redis Token TTL 保持一致（7 天） */
    public static final int COOKIE_MAX_AGE = 7 * 24 * 3600;

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> PUBLIC_WRITE_PATHS = Set.of(
            "/api/users/login", "/api/users/register", "/api/users/logout");

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = resolveToken(request);
        if (token != null) {
            Optional<String> userId = authService.getUserIdByToken(token);
            if (userId.isPresent()) {
                request.setAttribute(CURRENT_USER_ID, userId.get());
                log.debug("Token 认证通过 userId={}", userId.get());
            }
        }

        // 写接口登录门槛
        if (WRITE_METHODS.contains(request.getMethod())
                && !PUBLIC_WRITE_PATHS.contains(request.getRequestURI())
                && request.getAttribute(CURRENT_USER_ID) == null) {
            log.warn("未登录写请求被拦截: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\",\"data\":null}");
            return false;
        }
        return true;
    }

    /**
     * 从请求中解析 Token：优先 HttpOnly Cookie，回退 Authorization Bearer 头。
     */
    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}
