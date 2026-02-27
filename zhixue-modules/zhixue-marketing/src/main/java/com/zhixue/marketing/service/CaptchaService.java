package com.zhixue.marketing.service;

public interface CaptchaService {

    String generateCaptcha(Long userId);

    boolean verifyCaptcha(Long userId, String code);

    boolean verifySlideCaptcha(Long userId, String code, String token);
}
