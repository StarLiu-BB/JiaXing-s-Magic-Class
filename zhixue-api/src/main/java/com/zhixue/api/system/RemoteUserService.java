package com.zhixue.api.system;

import com.zhixue.api.system.domain.LoginUser;
import com.zhixue.api.system.factory.RemoteUserFallbackFactory;
import com.zhixue.common.core.constant.ServiceNameConstants;
import com.zhixue.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/system/user/info/{username}")
    R<LoginUser> getUserInfo(@PathVariable("username") String username,
                             @RequestHeader(value = "X-Inner-Call", required = false) String innerHeader);

    @PostMapping("/system/user/login")
    R<LoginUser> login(@RequestBody LoginUser loginUser,
                       @RequestHeader(value = "X-Inner-Call", required = false) String innerHeader);
}

