package com.zhixue.common.core.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期时间工具类
 * 作用：这个类提供各种日期时间的处理方法。
 * 比如把日期转成字符串、把字符串转成日期、获取当前时间等。
 * 避免大家各自写一套，统一用这个类来处理日期时间。
 */
public final class DateUtils {

    // 不让别人创建这个类的对象，因为只用来提供工具方法
    private DateUtils() {
    }

    // 默认的日期时间格式：年-月-日 时:分:秒
    public static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    // 获取当前的日期和时间
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    // 把日期时间转成字符串，使用默认格式
    public static String format(LocalDateTime time) {
        return format(time, DEFAULT_PATTERN);
    }

    // 把日期时间转成字符串，使用指定的格式
    public static String format(LocalDateTime time, String pattern) {
        if (time == null) {
            return null;
        }
        return time.format(DateTimeFormatter.ofPattern(pattern));
    }

    // 把字符串转成日期时间，使用默认格式
    public static LocalDateTime parseDateTime(String text) {
        return parseDateTime(text, DEFAULT_PATTERN);
    }

    // 把字符串转成日期时间，使用指定的格式
    public static LocalDateTime parseDateTime(String text, String pattern) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
    }

    // 把字符串转成日期（只有年月日），使用指定的格式
    public static LocalDate parseDate(String text, String pattern) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern));
    }

    // 把新版本的日期时间转成老版本的Date对象
    public static Date toDate(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    // 把老版本的Date对象转成新版本的日期时间
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}

