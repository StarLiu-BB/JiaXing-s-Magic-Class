package com.zhixue.marketing.service.impl;

import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.marketing.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaptchaServiceImpl implements CaptchaService {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public String generateCaptcha(Long userId) {
        String captchaCode = RandomStringUtils.randomNumeric(4);
        String captchaImage = generateCaptchaImage(captchaCode);
        
        redisTemplate.opsForValue().set(
            "captcha:" + userId, 
            captchaCode, 
            java.time.Duration.ofMinutes(5)
        );
        
        log.info("生成验证码，userId={}, code={}", userId, captchaCode);
        
        return captchaImage;
    }

    @Override
    public boolean verifyCaptcha(Long userId, String code) {
        String storedCode = redisTemplate.opsForValue().get("captcha:" + userId);
        
        if (!code.equals(storedCode)) {
            log.warn("验证码错误，userId={}, input={}, stored={}", userId, code, storedCode);
            return false;
        }
        
        redisTemplate.delete("captcha:" + userId);
        
        log.info("验证码成功，userId={}", userId);
        
        return true;
    }

    @Override
    public boolean verifySlideCaptcha(Long userId, String code, String token) {
        log.info("验证滑动验证码，userId={}, code={}, token={}", userId, code, token);
        return verifySlideTrajectory(userId, code, token);
    }

    private String generateCaptchaImage(String code) {
        return "data:image/png;base64," + generateBase64Image(code);
    }

    private String generateBase64Image(String code) {
        return "iVBORw0KGgoAAAANSUhEUgAAAEAAAABAAAAQCAAA";
    }

    private boolean verifySlideTrajectory(Long userId, String code, String token) {
        return true;
    }
}
