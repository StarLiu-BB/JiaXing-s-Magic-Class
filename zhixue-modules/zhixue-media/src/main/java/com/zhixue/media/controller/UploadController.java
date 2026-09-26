package com.zhixue.media.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequireLogin;
import com.zhixue.media.domain.dto.ChunkUploadDTO;
import com.zhixue.media.domain.dto.MergeChunkDTO;
import com.zhixue.media.domain.entity.MediaFile;
import com.zhixue.media.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 上传与媒资管理接口。
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class UploadController {

    private final FileService fileService;

    /**
     * 单文件上传（封面、富文本配图等小文件）。
     * 大文件仍走 /upload/chunk + /upload/merge 分片流程。
     */
    @RequireLogin
    @PostMapping("/upload")
    public R<MediaFile> upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) String bucket) {
        return R.ok(fileService.uploadSingle(file, bucket));
    }

    /**
     * 分片上传。必须登录：否则任何人都能往对象存储写文件。
     */
    @RequireLogin
    @PostMapping("/upload/chunk")
    public R<Void> uploadChunk(@Valid @ModelAttribute ChunkUploadDTO dto) {
        return fileService.uploadChunk(dto) ? R.ok() : R.fail("上传失败");
    }

    /**
     * 合并分片。
     */
    @RequireLogin
    @PostMapping("/upload/merge")
    public R<MediaFile> merge(@Valid @RequestBody MergeChunkDTO dto) {
        return R.ok(fileService.mergeChunks(dto));
    }

    /**
     * 获取文件详情。要求登录，避免匿名枚举他人媒资。
     */
    @RequireLogin
    @GetMapping("/file/{id}")
    public R<MediaFile> getFile(@PathVariable Long id) {
        return R.ok(fileService.getById(id));
    }

    /**
     * 批量查询文件。
     */
    @RequireLogin
    @PostMapping("/file/list")
    public R<List<MediaFile>> listByIds(@RequestBody List<Long> ids) {
        return R.ok(fileService.listByIds(ids));
    }

    /**
     * 文件管理分页。
     */
    @RequireLogin
    @GetMapping("/file/page")
    public R<PageResult<MediaFile>> page(PageQuery query,
                                         @RequestParam(required = false) String fileName,
                                         @RequestParam(required = false) Integer status) {
        return R.ok(fileService.page(query, fileName, status));
    }
}

