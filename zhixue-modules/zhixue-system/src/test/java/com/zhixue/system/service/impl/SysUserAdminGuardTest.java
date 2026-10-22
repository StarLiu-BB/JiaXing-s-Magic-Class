package com.zhixue.system.service.impl;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.system.domain.dto.UserDTO;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysUser;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysUserMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import com.zhixue.system.service.SysMenuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SysUserAdminGuardTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SysUserRoleMapper relations = mock(SysUserRoleMapper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SysUserServiceImpl service = new SysUserServiceImpl(users, relations, roles, mock(SysMenuService.class), encoder,
            new com.zhixue.system.service.support.AdminOperationGuard(roles), mock(com.zhixue.system.service.support.SessionRevoker.class));

    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }

    private void current(long id) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalAccessConstants.USER_ID_HEADER, String.valueOf(id));
        request.setAttribute(InternalAccessConstants.TRUSTED_REQUEST_ATTR, true);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private SysUser user(long id) {
        SysUser user = new SysUser(); user.setId(id); user.setStatus(0); return user;
    }

    @Test void cannotDeleteCurrentUserBeforeRemovingRelations() {
        current(1001L);
        assertThatThrownBy(() -> service.removeUser(1001L)).isInstanceOf(ServiceException.class).hasMessageContaining("当前登录账号");
        verifyNoInteractions(relations);
    }

    @Test void cannotDisableCurrentUserThroughStatusEndpoint() {
        current(1001L); when(users.selectById(1001L)).thenReturn(user(1001L));
        assertThatThrownBy(() -> service.changeStatus(1001L, 1)).isInstanceOf(ServiceException.class).hasMessageContaining("当前登录账号");
        verify(users, never()).updateById(any(SysUser.class));
    }

    @Test void genericUpdateCannotBypassCurrentUserStatusProtection() {
        current(1001L); when(users.selectById(1001L)).thenReturn(user(1001L));
        UserDTO dto = new UserDTO(); dto.setId(1001L); dto.setStatus(1);
        assertThatThrownBy(() -> service.updateUser(dto)).isInstanceOf(ServiceException.class).hasMessageContaining("当前登录账号");
        verify(users, never()).updateById(any(SysUser.class));
    }

    @Test void invalidStatusRejectedBeforeWrite() {
        when(users.selectById(1003L)).thenReturn(user(1003L));
        assertThatThrownBy(() -> service.changeStatus(1003L, 2)).isInstanceOf(ServiceException.class);
        verify(users, never()).updateById(any(SysUser.class));
    }

    @Test void lastEnabledAdminCannotBeDisabledAndLockPrecedesCount() {
        current(1004L); when(users.selectById(1001L)).thenReturn(user(1001L));
        when(roles.lockEnabledAdminRoles()).thenReturn(List.of(1L));
        when(roles.isEnabledAdmin(1001L)).thenReturn(1L);
        assertThatThrownBy(() -> service.changeStatus(1001L, 1)).isInstanceOf(ServiceException.class).hasMessageContaining("至少保留");
        var order = inOrder(roles);
        order.verify(roles, times(2)).lockEnabledAdminRoles();
        order.verify(roles).isEnabledAdmin(1001L);
        order.verify(roles).countOtherEnabledAdmins(1001L);
        verify(users, never()).updateById(any(SysUser.class));
    }

    @Test void currentAdminCannotRemoveOwnAdminRole() {
        current(1001L); when(users.selectById(1001L)).thenReturn(user(1001L));
        when(roles.lockEnabledAdminRoles()).thenReturn(List.of(1L)); when(roles.isEnabledAdmin(1001L)).thenReturn(1L);
        assertThatThrownBy(() -> service.assignRoles(1001L, List.of())).isInstanceOf(ServiceException.class).hasMessageContaining("当前登录账号");
        verify(relations, never()).physicalDeleteByUserId(1001L);
    }

    @Test void mutationLocksBeforeFirstUserReadToAvoidOldSnapshot() {
        when(users.selectById(1003L)).thenReturn(user(1003L));
        service.changeStatus(1003L, 1);
        var order = inOrder(roles, users);
        order.verify(roles).lockEnabledAdminRoles();
        order.verify(users).selectById(1003L);
    }

    @Test void profileReadSharesLockWithAuthorizationMutation() {
        service.getLoginUserByUsername("student");
        var order = inOrder(roles, users);
        order.verify(roles).lockEnabledAdminRoles();
        order.verify(users).selectOne(any());
    }

    @Test void invalidRolesMustNotDeleteExistingAssignments() {
        when(users.selectById(1003L)).thenReturn(user(1003L));
        when(roles.selectBatchIds(List.of(99L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.assignRoles(1003L, List.of(99L))).isInstanceOf(ServiceException.class);
        verify(relations, never()).physicalDeleteByUserId(1003L);
    }

    @Test void failedRoleInsertMustThrowSoTransactionCannotCommitPartialAssignments() {
        when(users.selectById(1003L)).thenReturn(user(1003L));
        SysRole role = new SysRole(); role.setId(3L);
        when(roles.selectBatchIds(List.of(3L))).thenReturn(List.of(role));
        when(relations.insert(any(com.zhixue.system.domain.entity.SysUserRole.class))).thenReturn(0);
        assertThatThrownBy(() -> service.assignRoles(1003L, List.of(3L))).isInstanceOf(ServiceException.class).hasMessageContaining("分配失败");
    }

    @Test void genericRoleUpdateCannotDisableOrRenameAdmin() {
        SysRole admin = new SysRole(); admin.setId(1L); admin.setRoleKey("ADMIN"); admin.setStatus(0);
        when(roles.selectById(1L)).thenReturn(admin);
        SysRoleServiceImpl roleService = new SysRoleServiceImpl(roles, mock(com.zhixue.system.mapper.SysRoleMenuMapper.class), relations, mock(com.zhixue.system.mapper.SysMenuMapper.class), mock(com.zhixue.system.service.support.SessionRevoker.class));
        SysRole update = new SysRole(); update.setId(1L); update.setStatus(1);
        assertThatThrownBy(() -> roleService.updateRole(update)).isInstanceOf(ServiceException.class);
        update.setStatus(0); update.setRoleKey("STUDENT");
        assertThatThrownBy(() -> roleService.updateRole(update)).isInstanceOf(ServiceException.class);
        verify(roles, never()).updateById(any(SysRole.class));
    }
}
