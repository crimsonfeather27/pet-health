package com.pethealth.exception;

/**
 * 依赖的基础设施（Redis / MongoDB 等）不可用，业务无法继续时抛出 → HTTP 503
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
