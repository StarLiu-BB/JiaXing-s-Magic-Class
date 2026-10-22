package com.zhixue.interaction.service;

import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DanmakuAuditService {
    public record Decision(int status, String remark) {}
    private final RemoteAiService remoteAiService;
    private final SecurityProperties securityProperties;

    public Decision audit(String content) {
        try {
            R<String> result = remoteAiService.auditText(Map.of("content", content), "true", securityProperties.getInternalToken());
            if (result != null && result.isSuccess() && result.getData() != null && !result.getData().isBlank()) {
                String text = result.getData().trim();
                return "OK".equalsIgnoreCase(text) ? new Decision(1, null)
                        : new Decision(2, text.substring(0, Math.min(text.length(), 255)));
            }
        } catch (RuntimeException unavailable) {
            log.warn("弹幕审核不可用，转待人工复核");
        }
        return new Decision(0, "审核不可用，待人工复核");
    }
}
