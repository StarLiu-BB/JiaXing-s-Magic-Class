package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysRoleMenu;
import com.zhixue.system.domain.entity.SysUserRole;
import com.zhixue.system.mapper.SysMenuMapper;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysRoleMenuMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import com.zhixue.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public List<SysRole> listRoles() {
        List<SysRole> roles = roleMapper.selectList(null);
        roles.forEach(role -> role.setMenuIds(listMenuIds(role.getId())));
        return roles;
    }

    @Override
    public SysRole getRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role != null) {
            role.setMenuIds(listMenuIds(id));
        }
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createRole(SysRole role) {
        boolean created = roleMapper.insert(role) > 0;
        if (created && role.getMenuIds() != null) {
            assignMenus(role.getId(), role.getMenuIds());
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRole(SysRole role) {
        if (role.getId() == null) {
            throw new ServiceException("角色ID不能为空");
        }
        boolean updated = roleMapper.updateById(role) > 0;
        if (updated && role.getMenuIds() != null) {
            assignMenus(role.getId(), role.getMenuIds());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeRole(Long id) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        return roleMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignMenus(Long roleId, List<Long> menuIds) {
        log.info("分配权限 roleId={}, menus={}", roleId, menuIds);
        if (roleId == null || roleMapper.selectById(roleId) == null) {
            throw new ServiceException("角色不存在");
        }
        if (menuIds == null) {
            return true;
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds.isEmpty()) {
            return true;
        }
        List<Long> targetMenuIds = menuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targetMenuIds.isEmpty()) {
            return true;
        }
        List<Long> existingMenuIds = menuMapper.selectBatchIds(targetMenuIds).stream()
                .filter(Objects::nonNull)
                .map(menu -> menu.getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (existingMenuIds.size() != targetMenuIds.size()) {
            throw new ServiceException("存在无效菜单，分配失败");
        }
        int inserted = 0;
        for (Long menuId : targetMenuIds) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            inserted += roleMenuMapper.insert(relation);
        }
        return inserted == targetMenuIds.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long roleId, Integer status) {
        if (roleId == null || status == null) {
            throw new ServiceException("角色ID和状态不能为空");
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ServiceException("角色不存在");
        }
        role.setStatus(status);
        return roleMapper.updateById(role) > 0;
    }

    @Override
    public List<Long> listMenuIds(Long roleId) {
        List<SysRoleMenu> relations = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream()
                .map(SysRoleMenu::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}
