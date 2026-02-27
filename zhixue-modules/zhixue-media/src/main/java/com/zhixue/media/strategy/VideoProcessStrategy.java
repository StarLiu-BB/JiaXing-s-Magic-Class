package com.zhixue.media.strategy;

import com.zhixue.media.domain.entity.MediaFile;

/**
 * 视频处理策略。
 */
public interface VideoProcessStrategy {

    /**
     * 当前策略是否支持处理指定类型。
     */
    boolean supports(String fileType);

    /**
     * 执行转码，返回转码后的对象名。
     */
    String transcode(MediaFile mediaFile) throws Exception;
}

