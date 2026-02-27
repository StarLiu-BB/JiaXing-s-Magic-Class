package com.zhixue.api.system.factory;

import com.zhixue.api.system.RemoteUserService;
import com.zhixue.api.system.domain.LoginUser;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 用户服务远程调用降级处理。
 * </p>
 */
@Slf4j
@Component
public class RemoteUserFallbackFactory implements FallbackFactory<RemoteUserService> {

    @Override
    public RemoteUserService create(Throwable cause) {
        log.error("调用系统服务失败: {}", cause.getMessage());
        return new RemoteUserService() {
            @Override
            public R<LoginUser> getUserInfo(String username, String innerHeader) {
                return R.fail(HttpStatus.SERVICE_UNAVAILABLE, "系统服务不可用");
            }

            @Override
            public R<LoginUser> login(LoginUser loginUser, String innerHeader) {
                return R.fail(HttpStatus.SERVICE_UNAVAILABLE, "系统服务不可用");
            }
        };
    }
}

