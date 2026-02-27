package com.zhixue.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.media.config.MinioConfig.MinioProperties;
import com.zhixue.media.domain.dto.ChunkUploadDTO;
import com.zhixue.media.domain.dto.MergeChunkDTO;
import com.zhixue.media.domain.entity.MediaFile;
import com.zhixue.media.mapper.MediaFileMapper;
import com.zhixue.media.service.FileService;
import com.zhixue.media.service.VideoProcessService;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 媒资文件服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final MediaFileMapper mediaFileMapper;
    private final VideoProcessService videoProcessService;

    @Override
    public boolean uploadChunk(ChunkUploadDTO dto) {
        String bucket = resolveBucket(dto.getBucket());
        String objectName = chunkObject(dto.getFileMd5(), dto.getChunkIndex());
        try {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(dto.getChunkFile().getInputStream(), dto.getChunkFile().getSize(), -1)
                    .contentType("application/octet-stream")
                    .build();
            minioClient.putObject(args);
            return true;
        } catch (Exception e) {
            log.error("上传分片失败，md5={} index={}", dto.getFileMd5(), dto.getChunkIndex(), e);
            throw new ServiceException("上传分片失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaFile mergeChunks(MergeChunkDTO dto) {
        String bucket = resolveBucket(dto.getBucket());
        String targetObject = mergedObject(dto.getFileMd5(), dto.getFileName());

        List<ComposeSource> sources = new ArrayList<>();
        IntStream.range(0, dto.getChunkTotal())
                .forEach(idx -> sources.add(ComposeSource.builder()
                        .bucket(bucket)
                        .object(chunkObject(dto.getFileMd5(), idx))
                        .build()));
        try {
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket)
                    .object(targetObject)
                    .sources(sources)
                    .build());
        } catch (Exception e) {
            log.error("合并分片失败，md5={}", dto.getFileMd5(), e);
            throw new ServiceException("合并分片失败: " + e.getMessage());
        } finally {
            removeChunksQuietly(bucket, dto.getFileMd5(), dto.getChunkTotal());
        }

        MediaFile mediaFile = upsertMediaRecord(bucket, targetObject, dto);

        if (isVideo(dto.getFileType())) {
            mediaFile.setStatus(2);
            mediaFileMapper.updateById(mediaFile);
            try {
                String newObject = videoProcessService.transcode(mediaFile);
                if (StringUtils.hasText(newObject)) {
                    mediaFile.setObjectName(newObject);
                    mediaFile.setFileUrl(buildUrl(bucket, newObject));
                    mediaFile.setStatus(3);
                } else {
                    mediaFile.setStatus(3);
                }
            } catch (Exception e) {
                log.error("视频转码失败，md5={}", dto.getFileMd5(), e);
                mediaFile.setStatus(4);
                mediaFile.setRemark("转码失败: " + e.getMessage());
            }
            mediaFileMapper.updateById(mediaFile);
        }

        return mediaFile;
    }

    @Override
    public MediaFile getById(Long id) {
        return mediaFileMapper.selectById(id);
    }

    @Override
    public List<MediaFile> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mediaFileMapper.selectBatchIds(ids);
    }

    @Override
    public PageResult<MediaFile> page(PageQuery query, String fileName, Integer status) {
        LambdaQueryWrapper<MediaFile> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(fileName)) {
            qw.like(MediaFile::getFileName, fileName);
        }
        if (status != null) {
            qw.eq(MediaFile::getStatus, status);
        }
        Page<MediaFile> page = mediaFileMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getSize());
    }

    private MediaFile upsertMediaRecord(String bucket, String objectName, MergeChunkDTO dto) {
        LambdaQueryWrapper<MediaFile> qw = new LambdaQueryWrapper<>();
        qw.eq(MediaFile::getFileMd5, dto.getFileMd5());
        MediaFile mediaFile = mediaFileMapper.selectOne(qw);
        boolean insert = false;
        if (Objects.isNull(mediaFile)) {
            mediaFile = new MediaFile();
            mediaFile.setStatus(1);
            insert = true;
        }
        mediaFile.setFileMd5(dto.getFileMd5());
        mediaFile.setFileName(dto.getFileName());
        mediaFile.setBucket(bucket);
        mediaFile.setObjectName(objectName);
        mediaFile.setFileUrl(buildUrl(bucket, objectName));
        mediaFile.setFileType(dto.getFileType());
        mediaFile.setFileSize(dto.getFileSize());

        if (insert) {
            mediaFileMapper.insert(mediaFile);
        } else {
            mediaFileMapper.updateById(mediaFile);
        }
        return mediaFile;
    }

    private String resolveBucket(String bucket) {
        return StringUtils.hasText(bucket) ? bucket : properties.getBucket();
    }

    private String chunkObject(String md5, int chunkIndex) {
        return properties.getChunkPath() + "/" + md5 + "/" + chunkIndex;
    }

    private String mergedObject(String md5, String fileName) {
        String safeName = StringUtils.hasText(fileName) ? fileName : md5;
        return "upload/" + md5 + "/" + safeName;
    }

    private void removeChunksQuietly(String bucket, String md5, int chunkTotal) {
        IntStream.range(0, chunkTotal).forEach(idx -> {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(chunkObject(md5, idx))
                        .build());
            } catch (Exception e) {
                log.warn("清理分片失败 md5={} index={}", md5, idx, e);
            }
        });
    }

    private boolean isVideo(String fileType) {
        return StringUtils.hasText(fileType) && fileType.toLowerCase().startsWith("video");
    }

    private String buildUrl(String bucket, String objectName) {
        String host = StringUtils.hasText(properties.getExternalUrl())
                ? properties.getExternalUrl()
                : properties.getEndpoint();
        return host + "/" + bucket + "/" + objectName;
    }
}

