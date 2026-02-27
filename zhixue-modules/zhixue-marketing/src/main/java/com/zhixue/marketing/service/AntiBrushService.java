package com.zhixue.marketing.service;

/**
 * 防刷服务接口
 */
public interface AntiBrushService {

    /**
     * 防刷检查
     *
     * @param userId   用户ID
     * @param ip       IP地址
     * @param deviceId 设备ID
     * @return 是否允许通过
     */
    boolean check(Long userId, String ip, String deviceId);

    /**
     * 判断用户是否在黑名单中
     *
     * @param userId 用户ID
     * @return 是否是黑名单用户
     */
    boolean isBlacklisted(Long userId);
}
