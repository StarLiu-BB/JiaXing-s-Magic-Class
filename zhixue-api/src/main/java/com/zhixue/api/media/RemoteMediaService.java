package com.zhixue.api.media;

import com.zhixue.api.media.domain.MediaFile;
import com.zhixue.api.media.factory.RemoteMediaFallbackFactory;
import com.zhixue.common.core.constant.ServiceNameConstants;
import com.zhixue.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * <p>
 * 媒资中心远程接口。
 * </p>
 */
@FeignClient(contextId = "remoteMediaService",
        value = ServiceNameConstants.MEDIA_SERVICE,
        fallbackFactory = RemoteMediaFallbackFactory.class)
@RequestMapping("/media")
public interface RemoteMediaService {

    @GetMapping("/file/{fileId}")
    R<MediaFile> getFile(@PathVariable("fileId") Long fileId);

    @PostMapping("/file/list")
    R<List<MediaFile>> listByIds(@RequestBody List<Long> fileIds);
}

