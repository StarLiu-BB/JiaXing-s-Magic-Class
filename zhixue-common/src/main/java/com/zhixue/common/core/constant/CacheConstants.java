package com.zhixue.common.core.constant;

import java.time.Duration;

/**
 * 缓存相关常量类
 * 作用：这个类专门用来存Redis缓存的key名字和过期时间。
 * Redis就像一个高速存取的仓库，我们往里面存东西时需要一个唯一的名字。
 * 这个类把所有用到的名字都统一定义在这里，方便管理和修改。
 */
public final class CacheConstants {

    // 不让别人创建这个类的对象，因为只用来存常量
    private CacheConstants() {
    }

    // 登录凭证在Redis里存的名字前缀
    public static final String LOGIN_TOKEN_KEY = "zhixue:auth:token:";
    // 验证码在Redis里存的名字前缀
    public static final String CAPTCHA_KEY = "zhixue:auth:captcha:";
    // 系统配置在Redis里存的名字前缀
    public static final String CONFIG_KEY = "zhixue:config:";
    // 数据字典在Redis里存的名字前缀
    public static final String DICT_KEY = "zhixue:dict:";

    // 默认缓存过期时间是2小时
    public static final Duration DEFAULT_EXPIRE = Duration.ofHours(2);
    // 验证码5分钟后就失效了
    public static final Duration CAPTCHA_EXPIRE = Duration.ofMinutes(5);
    // 登录凭证12小时后失效
    public static final Duration TOKEN_EXPIRE = Duration.ofHours(12);
}

