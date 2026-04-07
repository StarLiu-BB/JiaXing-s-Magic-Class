package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.api.system.domain.LoginUser;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.system.domain.dto.UserDTO;
import com.zhixue.system.domain.dto.UserQueryDTO;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysUser;
import com.zhixue.system.domain.entity.SysUserRole;
import com.zhixue.system.domain.vo.UserVO;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysUserMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import com.zhixue.system.service.SysMenuService;
import com.zhixue.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuService menuService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> listUsers(UserQueryDTO query) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getUsername())) {
            qw.like(SysUser::getUsername, query.getUsername());
        }
        if (StringUtils.isNotBlank(query.getPhone())) {
            qw.like(SysUser::getPhone, query.getPhone());
        }
        if (query.getStatus() != null) {
            qw.eq(SysUser::getStatus, query.getStatus());
        }
        if (query.getRoleId() != null) {
            List<Long> userIds = userRoleMapper.selectList(
                            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, query.getRoleId()))
                    .stream()
                    .map(SysUserRole::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (userIds.isEmpty()) {
                return PageResult.of(Collections.emptyList(), 0, query.getPageSize());
            }
            qw.in(SysUser::getId, userIds);
        }
        qw.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = userMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), qw);
        List<UserVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, page.getTotal(), query.getPageSize());
    }

    @Override
    public UserVO getUser(Long id) {
        SysUser user = userMapper.selectById(id);
        return user == null ? null : toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createUser(UserDTO dto) {
        if (StringUtils.isBlank(dto.getUsername())) {
            throw new ServiceException("用户名不能为空");
        }
        if (StringUtils.isBlank(dto.getPassword())) {
            throw new ServiceException("密码不能为空");
        }

        SysUser entity = new SysUser();
        entity.setUsername(dto.getUsername());
        entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        entity.setNickname(dto.getNickname());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setRemark(dto.getRemark());
        entity.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        boolean created = userMapper.insert(entity) > 0;
        if (created) {
            assignRoles(entity.getId(), dto.getRoleIds());
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(UserDTO dto) {
        SysUser entity = userMapper.selectById(dto.getId());
        if (entity == null) {
            throw new ServiceException("用户不存在");
        }
        entity.setNickname(dto.getNickname());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setRemark(dto.getRemark());
        entity.setStatus(dto.getStatus());
        if (StringUtils.isNotBlank(dto.getPassword())) {
            entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        boolean updated = userMapper.updateById(entity) > 0;
        if (updated && dto.getRoleIds() != null) {
            assignRoles(entity.getId(), dto.getRoleIds());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeUser(Long id) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        return userMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignRoles(Long userId, List<Long> roleIds) {
        log.info("分配角色 userId={}, roles={}", userId, roleIds);
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new ServiceException("用户不存在");
        }
        if (roleIds == null) {
            return true;
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds.isEmpty()) {
            return true;
        }
        List<Long> targetRoleIds = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targetRoleIds.isEmpty()) {
            return true;
        }
        List<Long> existingRoleIds = roleMapper.selectBatchIds(targetRoleIds).stream()
                .filter(Objects::nonNull)
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (existingRoleIds.size() != targetRoleIds.size()) {
            throw new ServiceException("存在无效角色，分配失败");
        }
        int inserted = 0;
        for (Long roleId : targetRoleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            inserted += userRoleMapper.insert(relation);
        }
        return inserted == targetRoleIds.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetPassword(Long userId, String password) {
        if (userId == null || StringUtils.isBlank(password)) {
            throw new ServiceException("用户ID和密码不能为空");
        }
        SysUser entity = userMapper.selectById(userId);
        if (entity == null) {
            throw new ServiceException("用户不存在");
        }
        entity.setPassword(passwordEncoder.encode(password));
        return userMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long userId, Integer status) {
        if (userId == null || status == null) {
            throw new ServiceException("用户ID和状态不能为空");
        }
        SysUser entity = userMapper.selectById(userId);
        if (entity == null) {
            throw new ServiceException("用户不存在");
        }
        entity.setStatus(status);
        return userMapper.updateById(entity) > 0;
    }

    @Override
    public LoginUser getLoginUserByUsername(String username) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUser::getUsername, username);
        SysUser user = userMapper.selectOne(qw);
        return toLoginUser(user);
    }

    @Override
    public LoginUser getLoginUserByPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return null;
        }
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.eq(SysUser::getPhone, phone.trim());
        SysUser user = userMapper.selectOne(qw);
        return toLoginUser(user);
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setRemark(user.getRemark());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoleIds(listRoleIds(user.getId(), false));
        return vo;
    }

    private List<Long> listRoleIds(Long userId, boolean onlyEnabled) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (!onlyEnabled || roleIds.isEmpty()) {
            return roleIds;
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .filter(Objects::nonNull)
                .filter(role -> role.getStatus() == null || role.getStatus() == 0)
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private LoginUser toLoginUser(SysUser user) {
        if (user == null) {
            return null;
        }

        List<Long> roleIds = listRoleIds(user.getId(), true);
        List<String> roleKeys = roleIds.isEmpty()
                ? Collections.emptyList()
                : roleMapper.selectBatchIds(roleIds).stream()
                .filter(Objects::nonNull)
                .filter(role -> role.getStatus() == null || role.getStatus() == 0)
                .map(SysRole::getRoleKey)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());

        com.zhixue.api.system.domain.SysUser remoteUser = new com.zhixue.api.system.domain.SysUser();
        remoteUser.setId(user.getId());
        remoteUser.setUsername(user.getUsername());
        remoteUser.setPassword(user.getPassword());
        remoteUser.setNickname(user.getNickname());
        remoteUser.setPhone(user.getPhone());
        remoteUser.setEmail(user.getEmail());
        remoteUser.setAvatar(user.getAvatar());
        remoteUser.setStatus(user.getStatus());
        remoteUser.setCreateTime(user.getCreateTime());
        remoteUser.setUpdateTime(user.getUpdateTime());
        remoteUser.setDeleted(user.getDeleted());

        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .roles(roleKeys)
                .permissions(menuService.listPermissionsByUserId(user.getId()))
                .sysUser(remoteUser)
                .build();
    }
}
