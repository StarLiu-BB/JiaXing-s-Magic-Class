package com.zhixue.auth.service.impl;

import com.zhixue.auth.service.TokenService;
import com.zhixue.common.core.constant.CacheConstants;
import com.zhixue.common.security.session.LoginSessionRegistry;
import com.zhixue.common.core.utils.JwtUtils;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录凭证服务实现类
 * 作用：这个类实现了登录凭证的生成、刷新、删除和查询功能。
 * 采用登录凭证加Redis双重管理的方式：
 * 1. 生成登录凭证：根据用户信息生成一个加密的凭证字符串
 * 2. 缓存登录凭证：把用户信息存到Redis里，方便快速查询
 * 3. 刷新登录凭证：延长登录凭证的有效期
 * 4. 删除登录凭证：用户退出登录时，删除Redis里的缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final LoginSessionRegistry sessions;
    private final SecurityProperties securityProperties;

    @Override
    public String createToken(LoginUser loginUser) {
        if (loginUser.getAuthorizationRevision() == null) throw new com.zhixue.common.core.exception.ServiceException("缺少登录授权修订，请重新登录");
        Map<String, Object> claims = new HashMap<>();
        claims.put("jti", java.util.UUID.randomUUID().toString());
        claims.put("userId", loginUser.getUserId());
        claims.put("username", loginUser.getUsername());
        claims.put("roles", loginUser.getRoles());
        claims.put("permissions", extractPermissions(loginUser));
        Duration expiration = resolveTokenExpiration();
        String token = JwtUtils.generateToken(String.valueOf(loginUser.getUserId()), claims, securityProperties.getJwtSecret(),
                expiration.toSeconds());
        cacheToken(token, loginUser);
        log.info("用户登录成功，userId={}, username={}", loginUser.getUserId(), loginUser.getUsername());
        return token;
    }

    @Override
    public String refreshToken(String token) {
        String revision = sessions.revision();
        LoginUser loginUser = getLoginUser(token);
        if (loginUser == null) {
            return null;
        }
        loginUser.setAuthorizationRevision(revision);
        String refreshed = createToken(loginUser);
        logout(token);
        return refreshed;
    }

    @Override
    public void logout(String token) {
        sessions.logout(token);
        log.info("用户会话已登出");
    }

    @Override
    public LoginUser getLoginUser(String token) {
        return sessions.resolve(token);
    }

    private void cacheToken(String token, LoginUser loginUser) {
        sessions.register(token, loginUser, resolveTokenExpiration(), loginUser.getAuthorizationRevision());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractPermissions(LoginUser loginUser) {
        if (loginUser.getExtra() == null) {
            return java.util.Collections.emptyList();
        }
        Object permissions = loginUser.getExtra().get("permissions");
        if (permissions instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return java.util.Collections.emptyList();
    }

    private Duration resolveTokenExpiration() {
        long expirationSeconds = securityProperties.getJwtExpiration() > 0
                ? securityProperties.getJwtExpiration()
                : CacheConstants.TOKEN_EXPIRE.toSeconds();
        return Duration.ofSeconds(expirationSeconds);
    }
}
