package com.zhixue.system.service.impl;

import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.system.service.SysPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 权限校验实现，当前简化为日志输出，后续可接入 RBAC。
 * </p>
 */
@Slf4j
@Service
public class SysPermissionServiceImpl implements SysPermissionService {

    @Override
    public boolean hasPerm(String perm) {
        boolean allowed = SecurityUtils.hasAnyPermission(perm);
        log.info("校验权限 perm={}, allowed={}", perm, allowed);
        return allowed;
    }
}
