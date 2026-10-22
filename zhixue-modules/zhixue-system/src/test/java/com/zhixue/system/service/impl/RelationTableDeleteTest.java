package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.system.domain.entity.SysMenu;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysRoleMenu;
import com.zhixue.system.domain.entity.SysUser;
import com.zhixue.system.domain.entity.SysUserRole;
import com.zhixue.system.mapper.SysMenuMapper;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysRoleMenuMapper;
import com.zhixue.system.mapper.SysUserMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关联表必须物理删除。
 *
 * <p>修复前实测：sys_role_menu / sys_user_role 继承 BaseEntity 的 @TableLogic，
 * mapper.delete 实际执行 UPDATE deleted=1，旧行仍占着 (role_id, menu_id) 唯一键；
 * 只要新授权与旧授权有交集，重新插入即 Duplicate entry，接口返回 500。
 * 给管理员角色重新分配菜单、给 student 重新分配原有角色均复现。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelationTableDeleteTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysMenu.class);
        TableInfoHelper.initTableInfo(assistant, SysRoleMenu.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
    }

    private static String deleteSql(Class<?> mapper, String method) throws NoSuchMethodException {
        Method m = mapper.getMethod(method, Long.class);
        Delete delete = m.getAnnotation(Delete.class);
        assertThat(delete).as(mapper.getSimpleName() + "." + method + " 必须用 @Delete 写物理删除").isNotNull();
        return String.join(" ", delete.value()).trim().toUpperCase();
    }

    @Test
    void relationMappersExposePhysicalDelete() throws Exception {
        assertThat(deleteSql(SysRoleMenuMapper.class, "physicalDeleteByRoleId")).startsWith("DELETE FROM SYS_ROLE_MENU");
        assertThat(deleteSql(SysRoleMenuMapper.class, "physicalDeleteByMenuId")).startsWith("DELETE FROM SYS_ROLE_MENU");
        assertThat(deleteSql(SysUserRoleMapper.class, "physicalDeleteByUserId")).startsWith("DELETE FROM SYS_USER_ROLE");
        assertThat(deleteSql(SysUserRoleMapper.class, "physicalDeleteByRoleId")).startsWith("DELETE FROM SYS_USER_ROLE");
    }

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);

    private SysRoleServiceImpl roleService() {
        return new SysRoleServiceImpl(roleMapper, roleMenuMapper, userRoleMapper, menuMapper, mock(com.zhixue.system.service.support.SessionRevoker.class));
    }

    private static SysMenu menu(long id, long parentId, String perms) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setParentId(parentId);
        m.setPerms(perms);
        return m;
    }

    private static SysRole role(long id, String key) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setRoleKey(key);
        return r;
    }

    @Test
    void assignMenusClearsOldGrantsPhysically() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "TEACHER"));
        when(menuMapper.selectBatchIds(List.of(210L))).thenReturn(List.of(menu(210L, 200L, "course:list")));
        when(roleMenuMapper.insert(any(SysRoleMenu.class))).thenReturn(1);

        roleService().assignMenus(2L, List.of(210L));

        verify(roleMenuMapper).physicalDeleteByRoleId(2L);
        verify(roleMenuMapper, never()).delete(any());
    }

    @Test
    void removeRoleClearsRelationsPhysically() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "TEACHER"));
        when(roleMapper.deleteById(2L)).thenReturn(1);

        roleService().removeRole(2L);

        verify(roleMenuMapper).physicalDeleteByRoleId(2L);
        verify(userRoleMapper).physicalDeleteByRoleId(2L);
        verify(roleMenuMapper, never()).delete(any());
        verify(userRoleMapper, never()).delete(any());
    }

    @Test
    void removeMenuClearsGrantsPhysically() {
        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.deleteById(141L)).thenReturn(1);

        new SysMenuServiceImpl(menuMapper, userRoleMapper, roleMapper, roleMenuMapper, mock(com.zhixue.system.service.support.SessionRevoker.class)).removeMenu(141L);

        verify(roleMenuMapper).physicalDeleteByMenuId(141L);
        verify(roleMenuMapper, never()).delete(any());
    }

    /** 角色管理的查看/编辑权限：超级管理员一旦失去它们，就再也无法在界面上修改任何授权 */
    private void stubRecoveryMenus() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "ADMIN"));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(120L, 100L, "system:role:list"),
                menu(121L, 120L, "system:role:edit")));
    }

    @Test
    void superAdminMustKeepRoleManagementGrant() {
        stubRecoveryMenus();

        assertThatThrownBy(() -> roleService().assignMenus(1L, List.of(110L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("角色管理");
        assertThatThrownBy(() -> roleService().assignMenus(1L, List.of()))
                .isInstanceOf(ServiceException.class);
        verify(roleMenuMapper, never()).physicalDeleteByRoleId(any());
    }

    @Test
    void superAdminMayChangeOtherGrants() {
        stubRecoveryMenus();
        List<Long> target = List.of(100L, 110L, 120L, 121L);
        when(menuMapper.selectBatchIds(target)).thenReturn(List.of(
                menu(100L, 0L, null), menu(110L, 100L, "system:user:list"),
                menu(120L, 100L, "system:role:list"), menu(121L, 120L, "system:role:edit")));
        when(roleMenuMapper.insert(any(SysRoleMenu.class))).thenReturn(1);

        assertThat(roleService().assignMenus(1L, target)).isTrue();
        verify(roleMenuMapper).physicalDeleteByRoleId(1L);
    }

    @Test
    void userRoleReassignAndRemovalArePhysical() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser user = new SysUser();
        user.setId(1003L);
        when(userMapper.selectById(1003L)).thenReturn(user);
        when(userMapper.deleteById(1003L)).thenReturn(1);
        when(roleMapper.selectBatchIds(List.of(3L))).thenReturn(List.of(role(3L, "STUDENT")));
        when(userRoleMapper.insert(any(SysUserRole.class))).thenReturn(1);

        SysUserServiceImpl userService = new SysUserServiceImpl(userMapper, userRoleMapper, roleMapper, null, null, new com.zhixue.system.service.support.AdminOperationGuard(roleMapper), mock(com.zhixue.system.service.support.SessionRevoker.class));
        userService.assignRoles(1003L, List.of(3L));
        userService.removeUser(1003L);

        verify(userRoleMapper, org.mockito.Mockito.times(2)).physicalDeleteByUserId(1003L);
        verify(userRoleMapper, never()).delete(any());
    }
}
