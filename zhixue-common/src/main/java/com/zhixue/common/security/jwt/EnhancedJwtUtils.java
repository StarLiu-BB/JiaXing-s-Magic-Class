package com.zhixue.common.security.jwt;

import com.zhixue.common.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * 增强版登录凭证工具类
 * 作用：这个类用来生成和校验登录凭证（JWT令牌），密钥从配置文件中读取。
 * 会检查密钥的强度，确保至少是256位，保证安全性。
 * 用户登录后生成凭证，后续请求都带上这个凭证来证明身份。
 */
@Component
public class EnhancedJwtUtils {
    
    private static final Logger log = LoggerFactory.getLogger(EnhancedJwtUtils.class);
    // 密钥最少要有256位，保证加密强度
    private static final int MINIMUM_KEY_BITS = 256;
    private static final int MINIMUM_KEY_BYTES = MINIMUM_KEY_BITS / 8;
    
    private final SecurityProperties securityProperties;
    private final SecretKey secretKey;
    
    // 构造方法，初始化时从配置文件读取密钥并验证
    public EnhancedJwtUtils(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        
        String jwtSecret = securityProperties.getJwtSecret();
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            throw new IllegalStateException("JWT secret is not configured");
        }
        
        // 验证密钥强度
        byte[] keyBytes = decodeSecret(jwtSecret);
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                String.format("JWT secret must be at least %d bits (%d bytes), but was %d bytes",
                    MINIMUM_KEY_BITS, MINIMUM_KEY_BYTES, keyBytes.length));
        }
        
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT secret loaded and validated (key size: {} bits)", keyBytes.length * 8);
    }
    
    // 生成一个登录凭证，使用默认的过期时间
    public String generateToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, securityProperties.getJwtExpiration());
    }
    
    // 生成一个登录凭证，指定过期时间（单位是秒）
    public String generateToken(String subject, Map<String, Object> claims, long expireSeconds) {
        Instant now = Instant.now();
        Instant expire = now.plusSeconds(expireSeconds);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expire))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    // 解析登录凭证，获取里面的信息
    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    // 从登录凭证中提取指定的信息
    public <T> T getClaim(String token, Function<Claims, T> extractor) {
        Claims claims = parseClaims(token);
        return extractor.apply(claims);
    }
    
    // 判断登录凭证是不是已经过期了
    public boolean isTokenExpired(String token) {
        Date expiration = getClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }
    
    // 从登录凭证中获取用户ID
    public String getSubject(String token) {
        return getClaim(token, Claims::getSubject);
    }
    
    // 验证密钥强度是否足够
    // 返回true表示密钥至少有256位
    public boolean validateKeyStrength() {
        String jwtSecret = securityProperties.getJwtSecret();
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            return false;
        }
        
        byte[] keyBytes = decodeSecret(jwtSecret);
        return keyBytes.length >= MINIMUM_KEY_BYTES;
    }
    
    // 获取密钥的位数
    public int getKeySize() {
        String jwtSecret = securityProperties.getJwtSecret();
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            return 0;
        }
        
        byte[] keyBytes = decodeSecret(jwtSecret);
        return keyBytes.length * 8;
    }
    
    // 解码密钥，先尝试Base64解码，失败就用UTF-8编码
    private byte[] decodeSecret(String secret) {
        try {
            // 先尝试Base64解码
            return Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            // 失败就用UTF-8编码
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
