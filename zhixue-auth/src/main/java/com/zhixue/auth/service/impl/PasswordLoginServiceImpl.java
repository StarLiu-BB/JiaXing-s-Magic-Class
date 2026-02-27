package com.zhixue.auth.service.impl;

import com.zhixue.auth.form.LoginForm;
import com.zhixue.auth.service.LoginService;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.common.security.model.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 密码登录服务实现类
 * 作用：这个类实现了密码登录的功能。
 * 用户输入用户名和密码，系统验证用户名和密码是否正确。
 * 验证通过后，返回用户的登录信息。
 */
@Slf4j
@Service("passwordLoginService")
public class PasswordLoginServiceImpl implements LoginService {

    // 模拟用户数据（实际应从数据库获取）
    private static final Map<String, UserInfo> MOCK_USERS = new HashMap<>();
    
    static {
        // 管理员账号
        MOCK_USERS.put("admin", new UserInfo(1L, "admin", "123456", Arrays.asList("ADMIN")));
        // 教师账号
        MOCK_USERS.put("teacher", new UserInfo(2L, "teacher", "123456", Arrays.asList("TEACHER")));
        MOCK_USERS.put("teacher1", new UserInfo(3L, "teacher1", "123456", Arrays.asList("TEACHER")));
        MOCK_USERS.put("teacher2", new UserInfo(4L, "teacher2", "123456", Arrays.asList("TEACHER")));
        // 普通用户
        MOCK_USERS.put("user", new UserInfo(5L, "user", "123456", Arrays.asList("USER")));
    }

    @Override
    public LoginUser login(LoginForm form) {
        if (StringUtils.isBlank(form.getUsername()) || StringUtils.isBlank(form.getPassword())) {
            throw new ServiceException("用户名或密码不能为空");
        }
        
        // 查找用户
        UserInfo userInfo = MOCK_USERS.get(form.getUsername());
        if (userInfo == null) {
            throw new ServiceException("用户不存在");
        }
        
        // 验证密码
        if (!userInfo.password.equals(form.getPassword())) {
            throw new ServiceException("密码错误");
        }
        
        log.info("密码登录校验通过，username={}, roles={}", form.getUsername(), userInfo.roles);
        return LoginUser.builder()
                .userId(userInfo.userId)
                .username(userInfo.username)
                .roles(userInfo.roles)
                .build();
    }
    
    // 内部类：模拟用户信息
    private static class UserInfo {
        Long userId;
        String username;
        String password;
        List<String> roles;
        
        UserInfo(Long userId, String username, String password, List<String> roles) {
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.roles = roles;
        }
    }
}

