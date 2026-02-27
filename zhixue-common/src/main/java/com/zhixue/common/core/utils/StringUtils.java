package com.zhixue.common.core.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 字符串工具类
 * 作用：这个类提供各种字符串和集合的处理方法。
 * 比如判断字符串是不是空的、生成随机ID、判断集合是不是空的等。
 * 避免大家各自写一套，统一用这个类来处理这些常见的判断和操作。
 */
public final class StringUtils {

    // 不让别人创建这个类的对象，因为只用来提供工具方法
    private StringUtils() {
    }

    // 判断字符串是不是空的，包括null或者全是空格
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    // 判断字符串是不是有内容
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    // 如果字符串是空的，就返回默认值，否则返回原字符串
    public static String defaultIfBlank(String str, String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }

    // 生成一个随机的唯一ID，去掉中间的横线
    public static String randomUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // 判断集合是不是空的，包括null或者没有元素
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    // 判断Map是不是空的，包括null或者没有键值对
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    // 判断两个字符串是不是相等
    public static boolean equals(CharSequence a, CharSequence b) {
        return Objects.equals(a, b);
    }
}

