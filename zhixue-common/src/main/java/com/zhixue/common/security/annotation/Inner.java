package com.zhixue.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 内部服务调用注解
 * 作用：这个注解用来标记只能由内部服务调用的接口。
 * 外部用户不能直接访问这些接口，只有系统内部的其他服务才能调用。
 * 比如订单服务调用库存服务扣减库存，这种接口就加上这个注解。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inner {

    // 是否标记为内部接口，默认是true
    boolean value() default true;
}

