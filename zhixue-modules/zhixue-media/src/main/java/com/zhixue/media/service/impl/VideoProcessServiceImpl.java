package com.zhixue.media.service.impl;

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
            log.warn("未配置视频处理策略，直接返回原文件");
            return null;
        }
        return strategies.stream()
                .filter(strategy -> strategy.supports(mediaFile.getFileType()))
                .findFirst()
                .map(strategy -> {
                    try {
                        return strategy.transcode(mediaFile);
                    } catch (Exception e) {
                        throw new RuntimeException("转码失败", e);
                    }
                })
                .orElse(null);
    }
}

