package com.zhixue.system.service;

import com.zhixue.system.domain.entity.SysRole;

import java.util.List;

/**
 * <p>
 * 角色服务。
 * </p>
 */
public interface SysRoleService {

    List<SysRole> listRoles();

    SysRole getRole(Long id);

    boolean createRole(SysRole role);

    boolean updateRole(SysRole role);

    boolean removeRole(Long id);

    boolean assignMenus(Long roleId, List<Long> menuIds);
}

