package com.zhixue.common.core.domain;

import com.zhixue.common.core.constant.HttpStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Optional;

/**
 * 统一返回结果类
 * 作用：这个类用来包装所有接口的返回结果，让前端统一处理。
 * 不管接口返回什么数据，都用这个类包起来，包含状态码、提示信息和实际数据。
 * 这样前端就知道请求是成功了还是失败了，还能拿到具体的数据。
 *
 * @param <T> 实际返回的数据类型，可以是任何东西
 */
@Data
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 状态码，告诉前端是成功还是失败
    private int code;
    // 提示信息，告诉用户发生了什么
    private String msg;
    // 实际返回的数据
    private T data;

    // 空的构造方法
    public R() {
    }

    // 创建一个完整的返回结果
    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // 返回一个成功的结果，没有数据
    public static <T> R<T> ok() {
        return ok(null);
    }

    // 返回一个成功的结果，带上数据
    public static <T> R<T> ok(T data) {
        return new R<>(HttpStatus.SUCCESS, "操作成功", data);
    }

    // 返回一个失败的结果，只给错误信息
    public static <T> R<T> fail(String message) {
        return new R<>(HttpStatus.INTERNAL_SERVER_ERROR, message, null);
    }

    // 返回一个失败的结果，指定状态码和错误信息
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    // 判断这个结果是不是成功的
    public boolean isSuccess() {
        return this.code == HttpStatus.SUCCESS;
    }

    // 把数据包装成Optional，避免空指针异常
    public Optional<T> optionalData() {
        return Optional.ofNullable(data);
    }
}

