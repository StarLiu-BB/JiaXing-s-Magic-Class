package com.zhixue.common.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * 启动期安全凭据校验器。
 *
 * <p>在应用启动阶段强制校验 JWT 密钥与内部调用令牌：空值、强度不足、
 * 或使用已泄露到公开仓库的历史默认值时，直接终止启动。</p>
 *
 * <p>设计约束：只有显式声明了这两项配置的服务才会被校验，
 * 未声明的服务（media/order/marketing/ai）不受影响。</p>
 */
@AutoConfiguration
public class SecurityCredentialsValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SecurityCredentialsValidator.class);

    static final String JWT_SECRET_PROPERTY = "zhixue.security.jwt-secret";
    static final String INTERNAL_TOKEN_PROPERTY = "zhixue.security.internal-token";

    /** JWT 密钥最小强度：256 位 = 32 字节。 */
    private static final int MIN_JWT_SECRET_BYTES = 32;

    /** 内部调用令牌最小长度。 */
    private static final int MIN_INTERNAL_TOKEN_LENGTH = 16;

    private static final String REMEDIATION = "请用 `openssl rand -base64 48` 生成新值，并通过环境变量注入";

    /**
     * 已提交进本仓库 Git 历史、必须永久拒绝的凭据。
     * 这些值可从公开仓库检出，等同于公开明文。
     */
    private static final Set<String> COMPROMISED_VALUES = Set.of(
            "emhpeHVlY2xvdWRqd3RzZWNyZXRrZXkyMDI0bXVzdGJlYXRsZWFzdDI1NmJpdHNsb25n",
            "zhixuecloudjwtsecretkey2024mustbeatleast256bitslong",
            "local-dev-jwt-secret-change-before-prod-1234567890",
            "local-dev-internal-token-change-before-prod",
            "change-before-prod",
            "__generate__");

    private final Environment environment;

    public SecurityCredentialsValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validateJwtSecret(environment.getProperty(JWT_SECRET_PROPERTY));
        validateInternalToken(environment.getProperty(INTERNAL_TOKEN_PROPERTY));
    }

    private void validateJwtSecret(String secret) {
        if (secret == null) {
            // 本服务未声明该配置，跳过
            return;
        }
        String value = secret.trim();
        if (value.isEmpty()) {
            throw new IllegalStateException(
                    "安全配置缺失：" + JWT_SECRET_PROPERTY + " 为空。"
                            + "请设置环境变量 ZHIXUE_JWT_SECRET。" + REMEDIATION);
        }
        rejectIfCompromised(value, "ZHIXUE_JWT_SECRET", JWT_SECRET_PROPERTY);

        int bits = decode(value).length * 8;
        if (bits < MIN_JWT_SECRET_BYTES * 8) {
            throw new IllegalStateException(
                    "安全配置过弱：" + JWT_SECRET_PROPERTY + " 仅 " + bits + " 位，"
                            + "要求至少 256 位。请设置更强的 ZHIXUE_JWT_SECRET。" + REMEDIATION);
        }
        log.info("JWT 密钥校验通过（强度 {} 位）", bits);
    }

    private void validateInternalToken(String token) {
        if (token == null) {
            return;
        }
        String value = token.trim();
        if (value.isEmpty()) {
            throw new IllegalStateException(
                    "安全配置缺失：" + INTERNAL_TOKEN_PROPERTY + " 为空。"
                            + "请设置环境变量 ZHIXUE_INTERNAL_TOKEN。" + REMEDIATION);
        }
        rejectIfCompromised(value, "ZHIXUE_INTERNAL_TOKEN", INTERNAL_TOKEN_PROPERTY);

        if (value.length() < MIN_INTERNAL_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "安全配置过弱：" + INTERNAL_TOKEN_PROPERTY + " 长度不足 "
                            + MIN_INTERNAL_TOKEN_LENGTH + " 位。"
                            + "该令牌可绕过全部认证鉴权，必须足够随机。"
                            + "请设置更强的 ZHIXUE_INTERNAL_TOKEN。" + REMEDIATION);
        }
        log.info("内部调用令牌校验通过");
    }

    private void rejectIfCompromised(String value, String envName, String property) {
        // Base64 密钥大小写敏感，不能只比小写形式；占位符类值则按小写兜底匹配
        if (COMPROMISED_VALUES.contains(value) || COMPROMISED_VALUES.contains(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "安全配置已泄露：" + property + " 使用了已提交进 Git 历史的默认值，"
                            + "该值可从公开仓库检出，等同于公开明文。"
                            + "必须更换 " + envName + "。" + REMEDIATION);
        }
    }

    /** 先按 Base64 解码，失败则按 UTF-8 原文处理，与既有 JwtUtils 行为保持一致。 */
    private byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
