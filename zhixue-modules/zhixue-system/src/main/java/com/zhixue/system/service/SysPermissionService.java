package com.zhixue.system.service;

/**
 * <p>
 * 权限校验服务。
 * </p>
 */
public interface SysPermissionService {

    /**
     * 检查是否拥有指定权限标识。
     */
    boolean hasPerm(String perm);
}

