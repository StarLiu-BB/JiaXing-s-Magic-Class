package com.zhixue.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要登录的注解
 * 作用：这个注解用来标记接口需要用户登录后才能访问。
 * 如果用户没登录就访问带这个注解的接口，会被拦截并提示需要登录。
 * 可以放在类上或者方法上，用来控制接口是否需要登录。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}

