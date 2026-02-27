package com.zhixue.auth.controller;

import com.zhixue.auth.form.LoginForm;
import com.zhixue.auth.service.LoginService;
import com.zhixue.auth.service.TokenService;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.common.security.model.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录凭证控制器
 * 作用：这个类提供登录、刷新凭证、退出登录等接口。
 * 前端调用这些接口来完成用户认证相关的操作。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TokenController {

    private final BeanFactory beanFactory;
    private final TokenService tokenService;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginForm form) {
        LoginService loginService = resolveLoginService(form.getLoginType());
        LoginUser loginUser = loginService.login(form);
        String token = tokenService.createToken(loginUser);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", loginUser);
        return R.ok(data);
    }

    @PostMapping("/refresh")
    public R<Map<String, Object>> refresh(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        String refreshed = tokenService.refreshToken(token);
        if (refreshed == null) {
            return R.fail("令牌无效或已过期");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("token", refreshed);
        return R.ok(data);
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        tokenService.logout(token);
        return R.ok();
    }

    private LoginService resolveLoginService(String loginType) {
        if (StringUtils.isBlank(loginType)) {
            throw new ServiceException("登录类型不能为空");
        }
        String beanName = switch (loginType.toLowerCase()) {
            case "password" -> "passwordLoginService";
            case "sms" -> "smsLoginService";
            case "wechat" -> "wechatLoginService";
            default -> throw new ServiceException("不支持的登录类型");
        };
        return beanFactory.getBean(beanName, LoginService.class);
    }

    private String extractToken(String authorization) {
        if (StringUtils.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            throw new ServiceException("令牌格式不正确");
        }
        return authorization.substring(7);
    }
}

