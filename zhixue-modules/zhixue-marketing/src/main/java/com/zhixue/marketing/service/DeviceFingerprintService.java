package com.zhixue.marketing.service;

import org.springframework.stereotype.Service;

@Service
public interface DeviceFingerprintService {

    String generateFingerprint(String userAgent, String ip, String acceptLanguage);

    boolean checkDeviceLimit(String fingerprint);
}
