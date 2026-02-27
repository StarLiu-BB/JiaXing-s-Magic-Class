package com.zhixue.system.service.impl;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 角色服务实现。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;

    @Override
    public List<SysRole> listRoles() {
        return roleMapper.selectList(null);
    }

    @Override
    public SysRole getRole(Long id) {
        return roleMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createRole(SysRole role) {
        return roleMapper.insert(role) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRole(SysRole role) {
        if (role.getId() == null) {
            throw new ServiceException("角色ID不能为空");
        }
        return roleMapper.updateById(role) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeRole(Long id) {
        return roleMapper.deleteById(id) > 0;
    }

    @Override
    public boolean assignMenus(Long roleId, List<Long> menuIds) {
        log.info("分配权限 roleId={}, menus={}", roleId, menuIds);
        // TODO: 持久化角色菜单关联
        return true;
    }
}

