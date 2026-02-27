package com.zhixue.system.service;

import com.zhixue.system.domain.dto.UserDTO;
import com.zhixue.system.domain.dto.UserQueryDTO;
import com.zhixue.system.domain.vo.UserVO;
import com.zhixue.common.core.domain.PageResult;

import java.util.List;

/**
 * <p>
 * 用户领域服务。
 * </p>
 */
public interface SysUserService {

    PageResult<UserVO> listUsers(UserQueryDTO query);

    UserVO getUser(Long id);

    boolean createUser(UserDTO dto);

    boolean updateUser(UserDTO dto);

    boolean removeUser(Long id);

    boolean assignRoles(Long userId, List<Long> roleIds);
}

