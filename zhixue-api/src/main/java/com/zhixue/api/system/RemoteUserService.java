package com.zhixue.api.system;

import com.zhixue.api.system.domain.LoginUser;
import com.zhixue.api.system.factory.RemoteUserFallbackFactory;
import com.zhixue.common.core.constant.ServiceNameConstants;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.InternalAccessConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * <p>
 * 系统服务用户远程调用接口。
 * </p>
 */
@FeignClient(contextId = "remoteUserService",
        value = ServiceNameConstants.SYSTEM_SERVICE,
        fallbackFactory = RemoteUserFallbackFactory.class)
public interface RemoteUserService {

    @GetMapping("/user/profile/{username}")
    R<LoginUser> getUserInfo(@PathVariable("username") String username,
                             @RequestHeader(value = InternalAccessConstants.INNER_CALL_HEADER, required = false) String innerHeader,
                             @RequestHeader(value = InternalAccessConstants.INTERNAL_TOKEN_HEADER, required = false) String internalToken);

    @GetMapping("/user/profile/phone/{phone}")
    R<LoginUser> getUserInfoByPhone(@PathVariable("phone") String phone,
                                    @RequestHeader(value = InternalAccessConstants.INNER_CALL_HEADER, required = false) String innerHeader,
                                    @RequestHeader(value = InternalAccessConstants.INTERNAL_TOKEN_HEADER, required = false) String internalToken);
}
