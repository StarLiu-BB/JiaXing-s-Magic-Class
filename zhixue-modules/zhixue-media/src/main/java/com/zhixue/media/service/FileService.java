package com.zhixue.media.service;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.media.domain.dto.ChunkUploadDTO;
import com.zhixue.media.domain.dto.MergeChunkDTO;
import com.zhixue.media.domain.entity.MediaFile;

import java.util.List;

/**
 * 媒资文件服务。
 */
public interface FileService {

    /**
     * 上传单个分片。
     */
    boolean uploadChunk(ChunkUploadDTO dto);

    /**
     * 合并分片并写入数据库。
     */
    MediaFile mergeChunks(MergeChunkDTO dto);

    MediaFile getById(Long id);

    List<MediaFile> listByIds(List<Long> ids);

    PageResult<MediaFile> page(PageQuery query, String fileName, Integer status);
}

