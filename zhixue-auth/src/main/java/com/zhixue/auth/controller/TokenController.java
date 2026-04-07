package com.zhixue.auth.controller;

import com.zhixue.auth.form.LoginForm;
import com.zhixue.auth.service.LoginService;
import com.zhixue.auth.service.TokenService;
import com.zhixue.common.core.constant.CacheConstants;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
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
    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginForm form) {
        String loginType = normalizeLoginType(form);
        if ("password".equalsIgnoreCase(loginType)) {
            validateCaptcha(form);
        }
        LoginService loginService = resolveLoginService(loginType);
        LoginUser loginUser = loginService.login(form);
        String token = tokenService.createToken(loginUser);
        return R.ok(buildTokenPayload(token, loginUser));
    }

    @PostMapping("/refresh")
    public R<Map<String, Object>> refresh(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        String refreshed = tokenService.refreshToken(token);
        if (refreshed == null) {
            return R.fail(HttpStatus.UNAUTHORIZED, "令牌无效或已过期");
        }
        LoginUser loginUser = tokenService.getLoginUser(refreshed);
        return R.ok(buildTokenPayload(refreshed, loginUser));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        tokenService.logout(token);
        return R.ok();
    }

    @GetMapping("/user/info")
    public R<Map<String, Object>> userInfo(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = extractToken(authorization);
        LoginUser loginUser = tokenService.getLoginUser(token);
        if (loginUser == null) {
            return R.fail(HttpStatus.UNAUTHORIZED, "令牌无效或已过期");
        }

        return R.ok(buildUserPayload(loginUser));
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

    private String normalizeLoginType(LoginForm form) {
        if (StringUtils.isNotBlank(form.getLoginType())) {
            return form.getLoginType().trim().toLowerCase();
        }
        if (StringUtils.isNotBlank(form.getUsername()) && StringUtils.isNotBlank(form.getPassword())) {
            return "password";
        }
        throw new ServiceException("登录类型不能为空");
    }

    private String extractToken(String authorization) {
        if (StringUtils.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            throw new ServiceException("令牌格式不正确");
        }
        return authorization.substring(7);
    }

    private void validateCaptcha(LoginForm form) {
        if (StringUtils.isBlank(form.getUuid()) || StringUtils.isBlank(form.getCode())) {
            throw new ServiceException("验证码不能为空");
        }
        String key = CacheConstants.CAPTCHA_KEY + form.getUuid();
        String expected = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (StringUtils.isBlank(expected) || !expected.equalsIgnoreCase(form.getCode())) {
            throw new ServiceException("验证码错误或已过期");
        }
    }

    private Map<String, Object> buildTokenPayload(String token, LoginUser loginUser) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("tokenType", "Bearer");
        payload.put("expiresIn", resolveExpiresIn());
        payload.put("refreshToken", token);
        payload.put("user", loginUser == null ? java.util.Collections.emptyMap() : buildUserPayload(loginUser));
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildUserPayload(LoginUser loginUser) {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", loginUser.getUserId());
        result.put("username", loginUser.getUsername());
        result.put("roles", loginUser.getRoles() == null ? java.util.Collections.emptyList() : loginUser.getRoles());

        List<String> permissions = java.util.Collections.emptyList();
        String nickname = "";
        String avatar = "";
        if (loginUser.getExtra() != null) {
            Object rawPerms = loginUser.getExtra().get("permissions");
            if (rawPerms instanceof List<?> list) {
                permissions = list.stream().map(String::valueOf).toList();
            }
            nickname = String.valueOf(loginUser.getExtra().getOrDefault("nickname", ""));
            avatar = String.valueOf(loginUser.getExtra().getOrDefault("avatar", ""));
        }
        result.put("permissions", permissions);
        result.put("nickname", nickname);
        result.put("avatar", avatar);
        return result;
    }

    private long resolveExpiresIn() {
        long configured = securityProperties.getJwtExpiration();
        return configured > 0 ? configured : CacheConstants.TOKEN_EXPIRE.toSeconds();
    }
}
