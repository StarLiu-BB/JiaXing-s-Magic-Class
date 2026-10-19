package com.zhixue.api.ai;

import com.zhixue.api.ai.factory.RemoteAiFallbackFactory;
import com.zhixue.common.core.constant.ServiceNameConstants;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.InternalAccessConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * AI 服务远程调用接口。
 */
@FeignClient(contextId = "remoteAiService",
        value = ServiceNameConstants.AI_SERVICE,
        fallbackFactory = RemoteAiFallbackFactory.class)
public interface RemoteAiService {

    /**
     * 审核文本内容。仅限服务间调用（AI 服务会校验内部令牌与 X-Inner-Call）。
     *
     * @param body 请求体，包含 content 字段
     * @return 安全时 data 为 "OK"，否则为风险说明；模型不可用时为失败结果
     */
    @PostMapping("/audit/text")
    R<String> auditText(@RequestBody Map<String, String> body,
                        @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                        @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);
}
