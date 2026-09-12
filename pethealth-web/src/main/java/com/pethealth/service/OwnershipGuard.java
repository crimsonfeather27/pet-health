package com.pethealth.service;

import com.pethealth.exception.AccessDeniedException;
import com.pethealth.interceptor.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 资源属主校验：防止水平越权（IDOR）
 * <p>
 * 当前登录用户与资源属主不一致时抛 403。
 */
@Component
public class OwnershipGuard {

    /** 校验当前登录用户是否为资源属主 */
    public void check(HttpServletRequest request, String resourceOwnerId) {
        String userId = AuthContext.requireUserId(request);
        if (!userId.equals(resourceOwnerId)) {
            throw new AccessDeniedException("无权操作他人资源");
        }
    }
}
