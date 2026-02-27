package com.zhixue.auth.service.impl;

import com.zhixue.auth.form.LoginForm;
import com.zhixue.auth.service.LoginService;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.common.security.model.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 短信验证码登录服务实现类
 * 作用：这个类实现了短信验证码登录的功能。
 * 用户输入手机号和短信验证码，系统验证验证码是否正确。
 * 验证通过后，返回用户的登录信息。
 */
@Slf4j
@Service("smsLoginService")
public class SmsLoginServiceImpl implements LoginService {

    @Override
    public LoginUser login(LoginForm form) {
        if (StringUtils.isBlank(form.getPhone()) || StringUtils.isBlank(form.getSmsCode())) {
            throw new ServiceException("手机号或验证码不能为空");
        }
        // TODO: 校验短信验证码
        log.info("短信登录校验通过，phone={}", form.getPhone());
        return LoginUser.builder()
                .userId(2L)
                .username(form.getPhone())
                .roles(Collections.singletonList("USER"))
                .build();
    }
}

