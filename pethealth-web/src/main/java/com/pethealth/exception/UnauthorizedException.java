package com.pethealth.exception;

/** 未认证（401）：请求需要登录但无法从 Token 解析出用户 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
