package com.zhixue.auth.service.impl;

import com.zhixue.api.system.RemoteUserService;
import com.zhixue.common.core.domain.R;
import com.zhixue.auth.form.LoginForm;
import com.zhixue.auth.service.LoginService;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 密码登录服务实现类
 * 作用：这个类实现了密码登录的功能。
 * 用户输入用户名和密码，系统验证用户名和密码是否正确。
 * 验证通过后，返回用户的登录信息。
 */
@Slf4j
@Service("passwordLoginService")
@RequiredArgsConstructor
public class PasswordLoginServiceImpl implements LoginService {

    private static final String INNER_CALL_HEADER = "true";

    private final RemoteUserService remoteUserService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    @Override
    public LoginUser login(LoginForm form) {
        if (StringUtils.isBlank(form.getUsername()) || StringUtils.isBlank(form.getPassword())) {
            throw new ServiceException("用户名或密码不能为空");
        }

        R<com.zhixue.api.system.domain.LoginUser> response =
                remoteUserService.getUserInfo(form.getUsername(), INNER_CALL_HEADER, securityProperties.getInternalToken());
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new ServiceException("用户不存在");
        }

        com.zhixue.api.system.domain.LoginUser remoteUser = response.getData();
        if (remoteUser.getSysUser() == null || remoteUser.getSysUser().getStatus() == null
                || remoteUser.getSysUser().getStatus() != 0) {
            throw new ServiceException("账号已停用");
        }

        String encodedPassword = remoteUser.getSysUser().getPassword();
        if (StringUtils.isBlank(encodedPassword) || !passwordEncoder.matches(form.getPassword(), encodedPassword)) {
            throw new ServiceException("密码错误");
        }

        Map<String, Object> extra = new HashMap<>();
        extra.put("permissions", remoteUser.getPermissions());
        extra.put("nickname", remoteUser.getSysUser().getNickname());
        extra.put("avatar", remoteUser.getSysUser().getAvatar());

        log.info("密码登录校验通过，username={}, roles={}", form.getUsername(), remoteUser.getRoles());
        return LoginUser.builder()
                .userId(remoteUser.getUserId())
                .username(remoteUser.getUsername())
                .roles(remoteUser.getRoles())
                .extra(extra)
                .build();
    }
}
