package com.zhixue.common.core.exception;

import com.zhixue.common.core.constant.HttpStatus;
import lombok.Getter;

/**
 * 业务异常类
 * 作用：这个类用来表示业务逻辑出错的情况。
 * 当业务规则不满足时，比如用户不存在、密码错误、余额不足等，就抛出这个异常。
 * 这样全局异常处理器就能统一捕获并返回友好的错误提示给前端。
 */
@Getter
public class ServiceException extends RuntimeException {

    // 错误状态码
    private final int code;

    // 创建一个异常，使用默认的错误码
    public ServiceException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    // 创建一个异常，指定错误码和错误信息
    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    // 创建一个异常，指定错误码、错误信息和原始异常原因
    public ServiceException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}

