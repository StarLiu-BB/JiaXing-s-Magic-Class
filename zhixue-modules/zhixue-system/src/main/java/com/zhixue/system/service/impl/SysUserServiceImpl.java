package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.system.domain.dto.UserDTO;
import com.zhixue.system.domain.dto.UserQueryDTO;
import com.zhixue.system.domain.entity.SysUser;
import com.zhixue.system.domain.vo.UserVO;
import com.zhixue.system.mapper.SysUserMapper;
import com.zhixue.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;

    @Override
    public PageResult<UserVO> listUsers(UserQueryDTO query) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getUsername())) {
            qw.like(SysUser::getUsername, query.getUsername());
        }
        if (query.getStatus() != null) {
            qw.eq(SysUser::getStatus, query.getStatus());
        }
        List<SysUser> users = userMapper.selectList(qw);
        List<UserVO> records = users.stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, records.size(), query.getPageSize());
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
        SysUser entity = new SysUser();
        entity.setUsername(dto.getUsername());
        entity.setPassword(dto.getPassword());
        entity.setNickname(dto.getNickname());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setStatus(dto.getStatus());
        return userMapper.insert(entity) > 0;
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
        entity.setStatus(dto.getStatus());
        if (StringUtils.isNotBlank(dto.getPassword())) {
            entity.setPassword(dto.getPassword());
        }
        return userMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeUser(Long id) {
        return userMapper.deleteById(id) > 0;
    }

    @Override
    public boolean assignRoles(Long userId, List<Long> roleIds) {
        log.info("分配角色 userId={}, roles={}", userId, roleIds);
        // TODO: 持久化用户角色关联
        return true;
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoleIds(Collections.emptyList());
        return vo;
    }
}

