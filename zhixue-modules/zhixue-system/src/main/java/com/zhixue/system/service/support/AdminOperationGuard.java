package com.zhixue.system.service.support;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AdminOperationGuard {
    private final SysRoleMapper roleMapper;

    public void checkUserChange(Long userId, Integer status, List<Long> nextRoles, boolean deleting) {
        validateStatus(status);
        boolean self = Objects.equals(SecurityUtils.getUserId(), userId) && userId != null;
        if (self && (deleting || Integer.valueOf(1).equals(status))) {
            throw new ServiceException("不能删除或停用当前登录账号");
        }
        List<Long> adminRoles = roleMapper.lockEnabledAdminRoles();
        boolean admin = !adminRoles.isEmpty() && roleMapper.isEnabledAdmin(userId) > 0;
        boolean losesAdmin = nextRoles != null && nextRoles.stream().noneMatch(adminRoles::contains);
        if (admin && (deleting || Integer.valueOf(1).equals(status) || losesAdmin)) {
            if (self && losesAdmin) throw new ServiceException("不能移除当前登录账号的超级管理员角色");
            if (roleMapper.countOtherEnabledAdmins(userId) == 0) {
                throw new ServiceException("至少保留一个启用的超级管理员");
            }
        }
    }

    public static void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) throw new ServiceException("状态仅允许0或1");
    }

    public static void checkRoleUpdate(SysRole existing, SysRole update) {
        validateStatus(update.getStatus());
        boolean admin = "ADMIN".equalsIgnoreCase(existing.getRoleKey());
        if (admin && (Integer.valueOf(1).equals(update.getStatus())
                || (update.getRoleKey() != null && !"ADMIN".equalsIgnoreCase(update.getRoleKey())))) {
            throw new ServiceException("超级管理员角色不允许停用或修改角色标识");
        }
        if (!admin && "ADMIN".equalsIgnoreCase(update.getRoleKey())) {
            throw new ServiceException("不允许将普通角色修改为超级管理员");
        }
    }
}
