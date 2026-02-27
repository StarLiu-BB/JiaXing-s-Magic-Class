package com.zhixue.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要角色权限的注解
 * 作用：这个注解用来标记接口需要什么样的角色才能访问。
 * 比如标注了@RequireRole("admin")的接口，只有管理员角色的用户才能访问。
 * 可以放在类上或者方法上，用来控制接口的访问权限。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    // 需要的角色列表，比如["admin", "teacher"]
    String[] value() default {};

    // 是否需要拥有所有角色，true表示必须全部拥有，false表示拥有其中一个就行
    boolean allMatch() default false;
}

