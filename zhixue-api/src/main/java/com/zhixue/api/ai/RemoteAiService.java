package com.zhixue.api.ai;

import com.zhixue.api.ai.factory.RemoteAiFallbackFactory;
import com.zhixue.common.core.constant.ServiceNameConstants;
import com.zhixue.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * AI 服务远程调用接口。
 */
@FeignClient(contextId = "remoteAiService",
        value = ServiceNameConstants.AI_SERVICE,
        fallbackFactory = RemoteAiFallbackFactory.class)
public interface RemoteAiService {

    /**
     * 审核文本内容。
     * @param body 请求体，包含 content 字段
     * @return 审核结果
     */
    @PostMapping("/audit/text")
    R<String> auditText(@RequestBody Map<String, String> body);
}
