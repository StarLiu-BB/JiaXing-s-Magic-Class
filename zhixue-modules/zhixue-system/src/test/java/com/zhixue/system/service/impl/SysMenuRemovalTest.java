package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.system.domain.entity.SysMenu;
import com.zhixue.system.domain.entity.SysRoleMenu;
import com.zhixue.system.mapper.SysMenuMapper;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysRoleMenuMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜单删除保护：原实现直接删除，父菜单被删后子菜单与按钮权限成为孤儿，
 * 在菜单树中消失却仍被角色授权引用。
 */
@ExtendWith(MockitoExtension.class)
class SysMenuRemovalTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysMenu.class);
        TableInfoHelper.initTableInfo(assistant, SysRoleMenu.class);
    }

    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;

    @Mock private com.zhixue.system.service.support.SessionRevoker sessionRevoker;

    @InjectMocks
    private SysMenuServiceImpl menuService;

    @Test
    void menuWithChildrenMustNotBeDeleted() {
        when(menuMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> menuService.removeMenu(100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("子菜单");
        verify(menuMapper, never()).deleteById(any(Long.class));
        verify(roleMenuMapper, never()).physicalDeleteByMenuId(any());
    }

    @Test
    void recoveryPermissionCannotBeDisabledOrDeleted() {
        SysMenu menu = new SysMenu(); menu.setId(141L); menu.setPerms("system:role:edit");
        when(menuMapper.selectList(null)).thenReturn(java.util.List.of(menu));
        SysMenu update = new SysMenu(); update.setId(141L); update.setStatus(1);
        assertThatThrownBy(() -> menuService.updateMenu(update)).isInstanceOf(ServiceException.class).hasMessageContaining("恢复权限");
        assertThatThrownBy(() -> menuService.removeMenu(141L)).isInstanceOf(ServiceException.class).hasMessageContaining("恢复权限");
        verify(menuMapper, never()).updateById(any(SysMenu.class));
        verify(menuMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void invalidMenuStatusCannotBypassRecoveryGuard() {
        SysMenu update = new SysMenu(); update.setId(141L); update.setStatus(2);
        assertThatThrownBy(() -> menuService.updateMenu(update)).isInstanceOf(ServiceException.class);
        verify(menuMapper, never()).updateById(any(SysMenu.class));
    }

    @Test
    void leafMenuCanBeDeleted() {
        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.deleteById(141L)).thenReturn(1);

        assertThat(menuService.removeMenu(141L)).isTrue();
        verify(roleMenuMapper).physicalDeleteByMenuId(141L);
    }
}
