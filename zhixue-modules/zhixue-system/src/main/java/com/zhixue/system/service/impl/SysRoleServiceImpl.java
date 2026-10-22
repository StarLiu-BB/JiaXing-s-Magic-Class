package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.system.domain.entity.SysMenu;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysRoleMenu;
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
    private final com.zhixue.system.service.support.SessionRevoker sessionRevoker;

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
        roleMapper.lockEnabledAdminRoles();
        boolean created = roleMapper.insert(role) > 0;
        if (created && role.getMenuIds() != null) {
            assignMenus(role.getId(), role.getMenuIds());
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRole(SysRole role) {
        roleMapper.lockEnabledAdminRoles();
        if (role.getId() == null) {
            throw new ServiceException("角色ID不能为空");
        }
        SysRole existing = roleMapper.selectById(role.getId());
        if (existing == null) throw new ServiceException("角色不存在");
        com.zhixue.system.service.support.AdminOperationGuard.checkRoleUpdate(existing, role);
        sessionRevoker.revokeRole(role.getId());
        boolean updated = roleMapper.updateById(role) > 0;
        if (updated && role.getMenuIds() != null) {
            assignMenus(role.getId(), role.getMenuIds());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeRole(Long id) {
        roleMapper.lockEnabledAdminRoles();
        rejectIfSuperAdmin(roleMapper.selectById(id), "删除");
        sessionRevoker.revokeRole(id);
        roleMenuMapper.physicalDeleteByRoleId(id);
        userRoleMapper.physicalDeleteByRoleId(id);
        return roleMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignMenus(Long roleId, List<Long> menuIds) {
        roleMapper.lockEnabledAdminRoles();
        log.info("分配权限 roleId={}, menus={}", roleId, menuIds);
        SysRole role = roleId == null ? null : roleMapper.selectById(roleId);
        if (role == null) {
            throw new ServiceException("角色不存在");
        }
        if (menuIds == null) {
            return true;
        }
        List<Long> targetMenuIds = menuIds.stream().filter(Objects::nonNull).distinct().toList();
        requireRecoveryGrant(role, targetMenuIds);
        if (!targetMenuIds.isEmpty()) {
            List<Long> existingMenuIds = menuMapper.selectBatchIds(targetMenuIds).stream()
                    .filter(Objects::nonNull)
                    .map(SysMenu::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (existingMenuIds.size() != targetMenuIds.size()) {
                throw new ServiceException("存在无效菜单，分配失败");
            }
        }
        sessionRevoker.revokeRole(roleId);
        roleMenuMapper.physicalDeleteByRoleId(roleId);
        if (targetMenuIds.isEmpty()) {
            return true;
        }
        int inserted = 0;
        for (Long menuId : targetMenuIds) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            inserted += roleMenuMapper.insert(relation);
        }
        if (inserted != targetMenuIds.size()) throw new ServiceException("菜单分配失败，请重试");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long roleId, Integer status) {
        roleMapper.lockEnabledAdminRoles();
        if (roleId == null || status == null) {
            throw new ServiceException("角色ID和状态不能为空");
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ServiceException("角色不存在");
        }
        if (status != 0) {
            rejectIfSuperAdmin(role, "停用");
        }
        com.zhixue.system.service.support.AdminOperationGuard.validateStatus(status);
        sessionRevoker.revokeRole(roleId);
        role.setStatus(status);
        return roleMapper.updateById(role) > 0;
    }

    /**
     * 超级管理员角色不可删除或停用：角色页开放写操作后，一次误点即可让所有管理员
     * 立即失去全部后台权限，且无法在界面上恢复。
     */
    private void rejectIfSuperAdmin(SysRole role, String action) {
        if (isSuperAdmin(role)) {
            throw new ServiceException("超级管理员角色不允许" + action);
        }
    }

    private static boolean isSuperAdmin(SysRole role) {
        return role != null && "ADMIN".equalsIgnoreCase(role.getRoleKey());
    }

    /** 超级管理员重新授权时必须保留的权限：缺了它们就再也无法在界面上修改任何授权 */
    private static final List<String> RECOVERY_PERMS = List.of("system:role:list", "system:role:edit");

    private void requireRecoveryGrant(SysRole role, List<Long> targetMenuIds) {
        if (!isSuperAdmin(role)) {
            return;
        }
        List<SysMenu> recoveryMenus = menuMapper.selectList(null);
        java.util.Set<String> granted = recoveryMenus.stream()
                .filter(menu -> targetMenuIds.contains(menu.getId()) && (menu.getStatus() == null || menu.getStatus() == 0))
                .filter(menu -> menu.getPerms() != null)
                .flatMap(menu -> java.util.Arrays.stream(menu.getPerms().split(","))).map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> required = recoveryMenus.stream()
                .filter(menu -> menu.getStatus() == null || menu.getStatus() == 0)
                .filter(menu -> menu.getPerms() != null && java.util.Arrays.stream(menu.getPerms().split(","))
                        .map(String::trim).anyMatch(RECOVERY_PERMS::contains))
                .map(SysMenu::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!targetMenuIds.containsAll(required) || !granted.containsAll(RECOVERY_PERMS)) {
            throw new ServiceException("超级管理员角色必须保留「角色管理」的查看与编辑权限，否则将无法再修改任何授权");
        }
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
