package com.pethealth.interceptor;

import com.pethealth.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录用户上下文工具
 * <p>
 * AuthInterceptor 已将 Token 认证结果写入 request attribute，
 * Controller 通过本工具取用；写接口取不到用户即抛 401。
 */
public final class AuthContext {

    private AuthContext() {
    }

    /** 获取当前登录用户 ID，未登录抛 UnauthorizedException（由 GlobalExceptionHandler 转 401） */
    public static String requireUserId(HttpServletRequest request) {
        String userId = userId(request);
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        return userId;
    }

    /** 获取当前登录用户 ID，未登录返回 null（用于读接口的可选降级逻辑） */
    public static String userId(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
        return attr instanceof String s ? s : null;
    }
}
