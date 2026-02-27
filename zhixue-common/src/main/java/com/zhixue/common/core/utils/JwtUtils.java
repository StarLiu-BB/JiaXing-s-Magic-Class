package com.zhixue.common.core.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * 登录凭证工具类
 * 作用：这个类用来生成和校验登录凭证（JWT令牌）。
 * 用户登录成功后，我们生成一个凭证给用户，用户后续请求都带上这个凭证。
 * 这个类负责生成凭证、解析凭证、判断凭证是否过期等操作。
 */
public final class JwtUtils {

    // 不让别人创建这个类的对象，因为只用来提供工具方法
    private JwtUtils() {
    }

    // 生成一个登录凭证，包含用户信息和过期时间
    public static String generateToken(String subject,
                                       Map<String, Object> claims,
                                       String base64Secret,
                                       long expireSeconds) {
        Instant now = Instant.now();
        Instant expire = now.plusSeconds(expireSeconds);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expire))
                .signWith(getKey(base64Secret), SignatureAlgorithm.HS256)
                .compact();
    }

    // 解析登录凭证，获取里面的信息
    public static Claims parseClaims(String token, String base64Secret) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey(base64Secret))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 从登录凭证中提取指定的信息
    public static <T> T getClaim(String token, String base64Secret, Function<Claims, T> extractor) {
        Claims claims = parseClaims(token, base64Secret);
        return extractor.apply(claims);
    }

    // 判断登录凭证是不是已经过期了
    public static boolean isTokenExpired(String token, String base64Secret) {
        Date expiration = getClaim(token, base64Secret, Claims::getExpiration);
        return expiration.before(new Date());
    }

    // 根据密钥生成加密用的钥匙
    private static SecretKey getKey(String base64Secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    }
}

