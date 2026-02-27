package com.zhixue.api.ai.factory;

import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 服务远程调用降级处理。
 */
@Slf4j
@Component
public class RemoteAiFallbackFactory implements FallbackFactory<RemoteAiService> {

    @Override
    public RemoteAiService create(Throwable cause) {
        log.error("AI 服务调用失败: {}", cause.getMessage());
        return new RemoteAiService() {
            @Override
            public R<String> auditText(Map<String, String> body) {
                return R.fail("AI 服务暂不可用: " + cause.getMessage());
            }
        };
    }
}
