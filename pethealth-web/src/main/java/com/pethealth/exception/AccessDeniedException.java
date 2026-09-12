package com.pethealth.exception;

/** 已登录但无权操作该资源（403）：水平越权防护 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
