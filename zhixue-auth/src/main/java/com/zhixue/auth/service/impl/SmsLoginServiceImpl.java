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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 短信验证码登录服务实现类
 * 作用：这个类实现了短信验证码登录的功能。
 * 用户输入手机号和短信验证码，系统验证验证码是否正确。
 * 验证通过后，返回用户的登录信息。
 */
@Slf4j
@Service("smsLoginService")
@RequiredArgsConstructor
public class SmsLoginServiceImpl implements LoginService {

    private static final String INNER_CALL_HEADER = "true";
    private static final String SMS_CODE_KEY_PREFIX = "zhixue:auth:sms:";

    private final StringRedisTemplate redisTemplate;
    private final RemoteUserService remoteUserService;
    private final SecurityProperties securityProperties;

    @Value("${zhixue.auth.sms.mode:sandbox}")
    private String smsMode;

    @Value("${zhixue.auth.sms.sandbox-code:123456}")
    private String sandboxSmsCode;

    @Override
    public LoginUser login(LoginForm form) {
        if (StringUtils.isBlank(form.getPhone()) || StringUtils.isBlank(form.getSmsCode())) {
            throw new ServiceException("手机号或验证码不能为空");
        }

        validateSmsCode(form.getPhone(), form.getSmsCode());

        R<com.zhixue.api.system.domain.LoginUser> response =
                remoteUserService.getUserInfoByPhone(form.getPhone(), INNER_CALL_HEADER, securityProperties.getInternalToken());
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new ServiceException("手机号未绑定账号");
        }

        com.zhixue.api.system.domain.LoginUser remoteUser = response.getData();
        if (remoteUser.getSysUser() == null || remoteUser.getSysUser().getStatus() == null
                || remoteUser.getSysUser().getStatus() != 0) {
            throw new ServiceException("账号已停用");
        }

        Map<String, Object> extra = new HashMap<>();
        extra.put("permissions", remoteUser.getPermissions());
        extra.put("nickname", remoteUser.getSysUser().getNickname());
        extra.put("avatar", remoteUser.getSysUser().getAvatar());

        log.info("短信登录校验通过，phone={}, username={}", form.getPhone(), remoteUser.getUsername());
        return LoginUser.builder()
                .userId(remoteUser.getUserId())
                .username(remoteUser.getUsername())
                .roles(remoteUser.getRoles())
                .extra(extra)
                .build();
    }

    private void validateSmsCode(String phone, String smsCode) {
        String key = SMS_CODE_KEY_PREFIX + phone;
        String expected = redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(expected)) {
            redisTemplate.delete(key);
            if (!expected.equalsIgnoreCase(smsCode)) {
                throw new ServiceException("短信验证码错误");
            }
            return;
        }
        if ("sandbox".equalsIgnoreCase(smsMode) && sandboxSmsCode.equals(smsCode)) {
            return;
        }
        throw new ServiceException("短信验证码错误或已过期");
    }
}
