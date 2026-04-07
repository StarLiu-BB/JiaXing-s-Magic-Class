package com.zhixue.media.service.impl;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.media.domain.entity.MediaFile;
import com.zhixue.media.service.VideoProcessService;
import com.zhixue.media.strategy.VideoProcessStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 视频处理服务实现，基于策略模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessServiceImpl implements VideoProcessService {

    private final List<VideoProcessStrategy> strategies;

    @Override
    public String transcode(MediaFile mediaFile) {
        if (CollectionUtils.isEmpty(strategies)) {
            log.warn("未配置视频处理策略，跳过转码并返回原对象名，fileId={}", mediaFile.getId());
            return mediaFile.getObjectName();
        }
        for (VideoProcessStrategy strategy : strategies) {
            if (!strategy.supports(mediaFile.getFileType())) {
                continue;
            }
            try {
                return strategy.transcode(mediaFile);
            } catch (Exception e) {
                throw new ServiceException("转码失败: " + e.getMessage());
            }
        }
        log.warn("不存在匹配 fileType={} 的转码策略，返回原对象名", mediaFile.getFileType());
        return mediaFile.getObjectName();
    }
}
