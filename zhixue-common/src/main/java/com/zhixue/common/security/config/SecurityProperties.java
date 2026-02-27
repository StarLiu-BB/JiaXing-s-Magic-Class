package com.zhixue.common.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全配置属性类
 * 作用：这个类用来从配置文件中读取安全相关的配置。
 * 包括登录凭证的密钥、过期时间，以及数据库、Redis、文件存储、AI服务的密钥等。
 * 这些敏感信息都放在配置文件里，启动时自动加载到这个类中。
 */
@Component
@ConfigurationProperties(prefix = "zhixue.security")
public class SecurityProperties {
    
    // 登录凭证的密钥，用来加密和解密凭证
    private String jwtSecret;
    // 登录凭证的有效期，默认是7200秒（2小时）
    private long jwtExpiration = 7200;
    // 加密相关的配置
    private Encryption encryption = new Encryption();
    // 数据库相关的配置
    private Database database = new Database();
    // Redis相关的配置
    private Redis redis = new Redis();
    // 文件存储相关的配置
    private Minio minio = new Minio();
    // AI服务相关的配置
    private Ai ai = new Ai();
    
    // 获取登录凭证密钥
    public String getJwtSecret() {
        return jwtSecret;
    }
    
    // 设置登录凭证密钥
    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }
    
    // 获取登录凭证有效期
    public long getJwtExpiration() {
        return jwtExpiration;
    }
    
    // 设置登录凭证有效期
    public void setJwtExpiration(long jwtExpiration) {
        this.jwtExpiration = jwtExpiration;
    }
    
    // 获取加密配置
    public Encryption getEncryption() {
        return encryption;
    }
    
    // 设置加密配置
    public void setEncryption(Encryption encryption) {
        this.encryption = encryption;
    }
    
    // 获取数据库配置
    public Database getDatabase() {
        return database;
    }
    
    // 设置数据库配置
    public void setDatabase(Database database) {
        this.database = database;
    }
    
    // 获取Redis配置
    public Redis getRedis() {
        return redis;
    }
    
    // 设置Redis配置
    public void setRedis(Redis redis) {
        this.redis = redis;
    }
    
    // 获取文件存储配置
    public Minio getMinio() {
        return minio;
    }
    
    // 设置文件存储配置
    public void setMinio(Minio minio) {
        this.minio = minio;
    }
    
    // 获取AI服务配置
    public Ai getAi() {
        return ai;
    }
    
    // 设置AI服务配置
    public void setAi(Ai ai) {
        this.ai = ai;
    }
    
    // 加密相关的配置
    public static class Encryption {
        // 加密密钥
        private String key;
        
        // 获取加密密钥
        public String getKey() {
            return key;
        }
        
        // 设置加密密钥
        public void setKey(String key) {
            this.key = key;
        }
    }
    
    // 数据库相关的配置
    public static class Database {
        // 数据库密码
        private String password;
        
        // 获取数据库密码
        public String getPassword() {
            return password;
        }
        
        // 设置数据库密码
        public void setPassword(String password) {
            this.password = password;
        }
    }
    
    // Redis相关的配置
    public static class Redis {
        // Redis密码
        private String password;
        
        // 获取Redis密码
        public String getPassword() {
            return password;
        }
        
        // 设置Redis密码
        public void setPassword(String password) {
            this.password = password;
        }
    }
    
    // 文件存储相关的配置
    public static class Minio {
        // 访问密钥
        private String accessKey;
        // 秘密密钥
        private String secretKey;
        
        // 获取访问密钥
        public String getAccessKey() {
            return accessKey;
        }
        
        // 设置访问密钥
        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }
        
        // 获取秘密密钥
        public String getSecretKey() {
            return secretKey;
        }
        
        // 设置秘密密钥
        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
    
    // AI服务相关的配置
    public static class Ai {
        // AI服务的API密钥
        private String apiKey;
        
        // 获取AI服务的API密钥
        public String getApiKey() {
            return apiKey;
        }
        
        // 设置AI服务的API密钥
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
