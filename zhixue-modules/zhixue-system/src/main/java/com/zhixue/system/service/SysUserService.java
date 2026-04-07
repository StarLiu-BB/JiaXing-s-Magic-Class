package com.zhixue.system.service;

import com.zhixue.api.system.domain.LoginUser;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.system.domain.dto.UserDTO;
import com.zhixue.system.domain.dto.UserQueryDTO;
import com.zhixue.system.domain.vo.UserVO;

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

    boolean resetPassword(Long userId, String password);

    boolean changeStatus(Long userId, Integer status);

    LoginUser getLoginUserByUsername(String username);

    LoginUser getLoginUserByPhone(String phone);
}
