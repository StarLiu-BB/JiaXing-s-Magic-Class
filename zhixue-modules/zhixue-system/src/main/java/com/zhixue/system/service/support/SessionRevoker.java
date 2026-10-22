package com.zhixue.system.service.support;

import com.zhixue.common.security.session.LoginSessionRegistry;
import com.zhixue.system.domain.entity.SysUserRole;
import com.zhixue.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionRevoker {
    private final LoginSessionRegistry sessions;
    private final SysUserRoleMapper userRoleMapper;

    public void revokeUser(Long userId) { sessions.revokeUser(userId); }

    public void revokeRole(Long roleId) {
        userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).distinct().forEach(sessions::revokeUser);
    }

    public void revokeAll() { sessions.revokeAll(); }
}
