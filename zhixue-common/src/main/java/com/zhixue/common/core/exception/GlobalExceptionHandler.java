package com.zhixue.common.core.exception;

import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

/**
 * 全局异常处理器
 * 作用：这个类专门用来捕获和处理所有的异常。
 * 不管程序哪里出错了，都会被这个类拦截，然后统一返回给前端。
 * 这样前端就能收到统一格式的错误提示，而不是看到一堆乱七八糟的错误信息。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理业务异常，比如用户不存在、密码错误等
    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return R.fail(ex.getCode(), ex.getMessage());
    }

    // 处理参数校验异常，比如前端传的数据格式不对
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = Optional.ofNullable(ex.getBindingResult().getFieldError())
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("参数校验失败");
        return R.fail(HttpStatus.BAD_REQUEST, message);
    }

    // 处理所有其他异常，这是最后的保底，任何没被上面捕获的异常都会到这里
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return R.fail(HttpStatus.INTERNAL_SERVER_ERROR, "系统异常，请稍后再试");
    }
}

