package com.zhixue.system.service.impl;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.system.domain.entity.SysMenu;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysRoleMenu;
import com.zhixue.system.mapper.SysMenuMapper;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysRoleMenuMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysRoleServiceImplTest {

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysRoleMenuMapper roleMenuMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysMenuMapper menuMapper;

    @InjectMocks
    private SysRoleServiceImpl roleService;

    @Test
    void shouldAssignDistinctMenus() {
        SysRole role = new SysRole();
        role.setId(2L);
        when(roleMapper.selectById(2L)).thenReturn(role);

        SysMenu m1 = new SysMenu();
        m1.setId(201L);
        SysMenu m2 = new SysMenu();
        m2.setId(202L);
        when(menuMapper.selectBatchIds(List.of(201L, 202L))).thenReturn(List.of(m1, m2));
        when(roleMenuMapper.insert(any(SysRoleMenu.class))).thenReturn(1);

        boolean ok = roleService.assignMenus(2L, List.of(201L, 201L, 202L));
        assertThat(ok).isTrue();

        ArgumentCaptor<SysRoleMenu> captor = ArgumentCaptor.forClass(SysRoleMenu.class);
        verify(roleMenuMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysRoleMenu::getMenuId).containsExactly(201L, 202L);
    }

    @Test
    void shouldRejectInvalidMenus() {
        SysRole role = new SysRole();
        role.setId(2L);
        when(roleMapper.selectById(2L)).thenReturn(role);

        SysMenu m1 = new SysMenu();
        m1.setId(201L);
        when(menuMapper.selectBatchIds(List.of(201L, 999L))).thenReturn(List.of(m1));

        assertThatThrownBy(() -> roleService.assignMenus(2L, List.of(201L, 999L)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("存在无效菜单，分配失败");
    }
}

