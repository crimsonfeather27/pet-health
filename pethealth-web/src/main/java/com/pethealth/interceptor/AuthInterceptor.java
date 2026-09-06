package com.pethealth.interceptor;

import com.pethealth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * 解析 Authorization Bearer Token，将 userId 注入 request attribute
 * <p>
 * 设计原则：不强制拦截 — 未登录请求也放行（业务 Controller 自行决定是否要求登录）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ID = "currentUserId";
    public static final String CURRENT_USERNAME = "currentUsername";

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            Optional<String> userId = authService.getUserIdByToken(token);
            if (userId.isPresent()) {
                request.setAttribute(CURRENT_USER_ID, userId.get());
                log.debug("Token 认证通过 userId={}", userId.get());
            }
        }
        return true;  // 不阻断请求
    }
}
