package com.zhixue.common.security.context;

import com.zhixue.common.security.model.LoginUser;

/**
 * 用户上下文持有者类
 * 作用：这个类用来在当前线程中存储和获取登录用户的信息。
 * 每个请求都会在一个独立的线程里处理，我们用这个类把用户信息存在当前线程里。
 * 这样在同一个请求的任何地方，都能方便地拿到当前登录用户是谁。
 */
public final class SecurityContextHolder {

    // 用ThreadLocal来存储当前线程的用户信息
    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    // 不让别人创建这个类的对象，因为只用来提供工具方法
    private SecurityContextHolder() {
    }

    // 把登录用户信息存到当前线程里
    public static void setLoginUser(LoginUser loginUser) {
        CONTEXT.set(loginUser);
    }

    // 从当前线程里获取登录用户信息
    public static LoginUser getLoginUser() {
        return CONTEXT.get();
    }

    // 清空当前线程的用户信息，避免内存泄漏
    public static void clear() {
        CONTEXT.remove();
    }
}

