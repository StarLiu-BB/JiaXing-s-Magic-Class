package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.system.domain.entity.SysMenu;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysRoleMenu;
import com.zhixue.system.domain.entity.SysUserRole;
import com.zhixue.system.domain.vo.RouterVO;
import com.zhixue.system.mapper.SysMenuMapper;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysRoleMenuMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import com.zhixue.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @Override
    public List<SysMenu> listMenus() {
        List<SysMenu> menus = menuMapper.selectList(null);
        menus.sort(Comparator.comparing(menu -> menu.getOrderNum() == null ? Integer.MAX_VALUE : menu.getOrderNum()));
        return menus;
    }

    @Override
    public SysMenu getMenu(Long id) {
        return menuMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createMenu(SysMenu menu) {
        return menuMapper.insert(menu) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateMenu(SysMenu menu) {
        if (menu.getId() == null) {
            throw new ServiceException("菜单ID不能为空");
        }
        return menuMapper.updateById(menu) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeMenu(Long id) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        return menuMapper.deleteById(id) > 0;
    }

    @Override
    public List<RouterVO> buildRouters(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> childrenMap = menus.stream()
                .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));
        return buildChildren(childrenMap, 0L);
    }

    @Override
    public List<SysMenu> listMenusByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> activeRoleIds = roleMapper.selectBatchIds(roleIds).stream()
                .filter(Objects::nonNull)
                .filter(role -> role.getStatus() == null || role.getStatus() == 0)
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (activeRoleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> menuIds = roleMenuMapper.selectList(
                        new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, activeRoleIds))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysMenu> menus = menuMapper.selectBatchIds(menuIds).stream()
                .filter(Objects::nonNull)
                .filter(menu -> menu.getStatus() == null || menu.getStatus() == 0)
                .collect(Collectors.toList());
        menus.sort(Comparator.comparing(menu -> menu.getOrderNum() == null ? Integer.MAX_VALUE : menu.getOrderNum()));
        return menus;
    }

    @Override
    public List<String> listPermissionsByUserId(Long userId) {
        return listMenusByUserId(userId).stream()
                .map(SysMenu::getPerms)
                .filter(StringUtils::isNotBlank)
                .flatMap(perms -> Arrays.stream(perms.split(",")))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    @Override
    public List<Long> listMenuIdsByRoleId(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<RouterVO> buildChildren(Map<Long, List<SysMenu>> map, Long parentId) {
        List<SysMenu> children = map.getOrDefault(parentId, new ArrayList<>());
        List<RouterVO> routers = new ArrayList<>();
        for (SysMenu menu : children) {
            RouterVO vo = new RouterVO();
            vo.setName(menu.getMenuName());
            vo.setPath(menu.getPath());
            vo.setComponent(menu.getComponent());
            vo.setIcon(menu.getIcon());
            vo.setHidden(menu.getVisible() != null && menu.getVisible() == 0);
            vo.setChildren(buildChildren(map, menu.getId()));
            routers.add(vo);
        }
        return routers;
    }
}
