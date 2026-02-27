package com.zhixue.api.media.factory;

import com.zhixue.api.media.RemoteMediaService;
import com.zhixue.api.media.domain.MediaFile;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <p>
 * 媒资服务降级处理。
 * </p>
 */
@Slf4j
@Component
public class RemoteMediaFallbackFactory implements FallbackFactory<RemoteMediaService> {

    @Override
    public RemoteMediaService create(Throwable cause) {
        log.error("调用媒资服务失败: {}", cause.getMessage());
        return new RemoteMediaService() {
            @Override
            public R<MediaFile> getFile(Long fileId) {
                return R.fail(HttpStatus.SERVICE_UNAVAILABLE, "媒资服务不可用");
            }

            @Override
            public R<List<MediaFile>> listByIds(List<Long> fileIds) {
                return R.fail(HttpStatus.SERVICE_UNAVAILABLE, "媒资服务不可用");
            }
        };
    }
}

