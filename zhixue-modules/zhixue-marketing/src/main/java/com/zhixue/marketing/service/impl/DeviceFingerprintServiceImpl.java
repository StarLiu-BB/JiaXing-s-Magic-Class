package com.zhixue.marketing.service.impl;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.marketing.service.AntiBrushService;
import com.zhixue.marketing.service.DeviceFingerprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceFingerprintServiceImpl implements DeviceFingerprintService {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public String generateFingerprint(String userAgent, String ip, String acceptLanguage) {
        String fingerprint = DigestUtils.md5Hex(
            userAgent + ip + acceptLanguage
        );
        
        redisTemplate.opsForValue().set(
            "device:" + fingerprint, 
            fingerprint, 
            java.time.Duration.ofHours(24)
        );
        
        log.info("生成设备指纹，fingerprint={}", fingerprint);
        
        return fingerprint;
    }

    @Override
    public boolean checkDeviceLimit(String fingerprint) {
        if (isInBlacklist(fingerprint)) {
            return false;
        }
        
        String key = "device:claim:" + fingerprint;
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count > 5) {
            return false;
        }
        
        return true;
    }

    private boolean isInBlacklist(String fingerprint) {
        String blacklistKey = "device:blacklist";
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(blacklistKey, fingerprint));
    }
}
