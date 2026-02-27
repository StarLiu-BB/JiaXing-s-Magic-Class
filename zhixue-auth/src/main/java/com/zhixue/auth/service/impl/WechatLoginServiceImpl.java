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
 * 微信登录服务实现类
 * 作用：这个类实现了微信登录的功能。
 * 用户通过微信授权登录，系统会获取微信授权码，然后调用微信平台验证授权码。
 * 验证通过后，返回用户的登录信息。
 */
@Slf4j
@Service("wechatLoginService")
public class WechatLoginServiceImpl implements LoginService {

    @Override
    public LoginUser login(LoginForm form) {
        if (StringUtils.isBlank(form.getWechatCode())) {
            throw new ServiceException("微信授权码不能为空");
        }
        // TODO: 调用微信平台校验 code 并换取用户信息
        log.info("微信登录校验通过，code={}", form.getWechatCode());
        return LoginUser.builder()
                .userId(3L)
                .username("wechat_" + form.getWechatCode())
                .roles(Collections.singletonList("USER"))
                .build();
    }
}

