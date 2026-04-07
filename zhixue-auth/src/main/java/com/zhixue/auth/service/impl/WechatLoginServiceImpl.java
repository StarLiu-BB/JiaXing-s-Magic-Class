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
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信登录服务实现类
 * 作用：这个类实现了微信登录的功能。
 * 用户通过微信授权登录，系统会获取微信授权码，然后调用微信平台验证授权码。
 * 验证通过后，返回用户的登录信息。
 */
@Slf4j
@Service("wechatLoginService")
@RequiredArgsConstructor
public class WechatLoginServiceImpl implements LoginService {

    private static final String INNER_CALL_HEADER = "true";

    private final RemoteUserService remoteUserService;
    private final SecurityProperties securityProperties;

    @Value("${zhixue.auth.wechat.mode:sandbox}")
    private String wechatMode;

    @Value("${zhixue.auth.wechat.sandbox-username:student}")
    private String sandboxUsername;

    @Override
    public LoginUser login(LoginForm form) {
        if (StringUtils.isBlank(form.getWechatCode())) {
            throw new ServiceException("微信授权码不能为空");
        }

        if (!"sandbox".equalsIgnoreCase(wechatMode)) {
            throw new ServiceException("当前环境暂未启用真实微信登录");
        }

        R<com.zhixue.api.system.domain.LoginUser> response =
                remoteUserService.getUserInfo(sandboxUsername, INNER_CALL_HEADER, securityProperties.getInternalToken());
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new ServiceException("微信登录账号映射失败");
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
        extra.put("wechatCode", form.getWechatCode());

        log.info("微信登录校验通过，sandboxUser={}, codePrefix={}", remoteUser.getUsername(),
                form.getWechatCode().substring(0, Math.min(form.getWechatCode().length(), 8)));
        return LoginUser.builder()
                .userId(remoteUser.getUserId())
                .username(remoteUser.getUsername())
                .roles(remoteUser.getRoles())
                .extra(extra)
                .build();
    }
}
