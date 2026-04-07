package com.zhixue.system.service.impl;

import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysUser;
import com.zhixue.system.domain.entity.SysUserRole;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysUserMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import com.zhixue.system.service.SysMenuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysMenuService menuService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SysUserServiceImpl userService;

    @Test
    void shouldAssembleLoginUserFromDatabaseRelations() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("encoded");
        user.setNickname("管理员");
        user.setStatus(0);

        SysUserRole relation = new SysUserRole();
        relation.setUserId(1L);
        relation.setRoleId(101L);

        SysRole role = new SysRole();
        role.setId(101L);
        role.setRoleKey("ADMIN");

        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation));
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(role));
        when(menuService.listPermissionsByUserId(1L)).thenReturn(List.of("system:user:list", "course:list"));

        com.zhixue.api.system.domain.LoginUser loginUser = userService.getLoginUserByUsername("admin");

        assertThat(loginUser).isNotNull();
        assertThat(loginUser.getUserId()).isEqualTo(1L);
        assertThat(loginUser.getRoles()).containsExactly("ADMIN");
        assertThat(loginUser.getPermissions()).contains("system:user:list", "course:list");
        assertThat(loginUser.getSysUser().getUsername()).isEqualTo("admin");
    }

    @Test
    void shouldPersistDistinctRolesWhenAssignRoles() {
        SysUser user = new SysUser();
        user.setId(9L);
        when(userMapper.selectById(9L)).thenReturn(user);

        SysRole role101 = new SysRole();
        role101.setId(101L);
        SysRole role102 = new SysRole();
        role102.setId(102L);

        when(roleMapper.selectBatchIds(eq(java.util.List.of(101L, 102L))))
                .thenReturn(java.util.List.of(role101, role102));
        when(userRoleMapper.delete(any())).thenReturn(1);
        when(userRoleMapper.insert(any(SysUserRole.class))).thenReturn(1);

        userService.assignRoles(9L, java.util.Arrays.asList(101L, 101L, 102L));

        ArgumentCaptor<SysUserRole> captor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SysUserRole::getRoleId)
                .containsExactly(101L, 102L);
    }

    @Test
    void shouldAssembleLoginUserByPhone() {
        SysUser user = new SysUser();
        user.setId(1003L);
        user.setUsername("student");
        user.setPhone("13800000003");
        user.setPassword("encoded");
        user.setStatus(0);

        SysUserRole relation = new SysUserRole();
        relation.setUserId(1003L);
        relation.setRoleId(3L);

        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleKey("STUDENT");

        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation));
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(role));
        when(menuService.listPermissionsByUserId(1003L)).thenReturn(List.of("course:list"));

        com.zhixue.api.system.domain.LoginUser loginUser = userService.getLoginUserByPhone("13800000003");

        assertThat(loginUser).isNotNull();
        assertThat(loginUser.getUsername()).isEqualTo("student");
        assertThat(loginUser.getRoles()).containsExactly("STUDENT");
    }
}
