package com.zhixue.media.service;

import com.zhixue.media.domain.entity.MediaFile;

/**
 * 视频处理服务。
 */
public interface VideoProcessService {

    /**
     * 按策略转码视频。
     *
     * @param mediaFile 待处理文件
     * @return 转码后对象名；无可用策略时返回 null
     */
    String transcode(MediaFile mediaFile);
}

