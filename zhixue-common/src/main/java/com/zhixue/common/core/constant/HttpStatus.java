package com.zhixue.common.core.constant;

/**
 * HTTP状态码常量类
 * 作用：这个类定义了各种请求的结果代码。
 * 当前端发请求过来时，我们用这些数字告诉它请求成功了还是失败了。
 * 比如200表示成功，401表示没登录，500表示服务器出问题了。
 */
public final class HttpStatus {

    // 不让别人创建这个类的对象，因为只用来存常量
    private HttpStatus() {
    }

    // 请求成功了
    public static final int SUCCESS = 200;
    // 创建成功了
    public static final int CREATED = 201;
    // 请求接收了，正在处理
    public static final int ACCEPTED = 202;

    // 请求参数有问题
    public static final int BAD_REQUEST = 400;
    // 用户没有登录
    public static final int UNAUTHORIZED = 401;
    // 用户登录了，但没有权限访问
    public static final int FORBIDDEN = 403;
    // 找不到请求的资源
    public static final int NOT_FOUND = 404;
    // 请求方式不对，比如该用GET却用了POST
    public static final int METHOD_NOT_ALLOWED = 405;
    // 数据冲突，比如重复创建已存在的东西
    public static final int CONFLICT = 409;

    // 服务器内部出错了
    public static final int INTERNAL_SERVER_ERROR = 500;
    // 服务暂时不可用
    public static final int SERVICE_UNAVAILABLE = 503;
}

