package com.zhixue.media.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.media.domain.dto.ChunkUploadDTO;
import com.zhixue.media.domain.dto.MergeChunkDTO;
import com.zhixue.media.domain.entity.MediaFile;
import com.zhixue.media.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
     * 分片上传。
     */
    @PostMapping("/upload/chunk")
    public R<Void> uploadChunk(@Valid @ModelAttribute ChunkUploadDTO dto) {
        return fileService.uploadChunk(dto) ? R.ok() : R.fail("上传失败");
    }

    /**
     * 合并分片。
     */
    @PostMapping("/upload/merge")
    public R<MediaFile> merge(@Valid @RequestBody MergeChunkDTO dto) {
        return R.ok(fileService.mergeChunks(dto));
    }

    /**
     * 获取文件详情（Feign）。
     */
    @GetMapping("/file/{id}")
    public R<MediaFile> getFile(@PathVariable Long id) {
        return R.ok(fileService.getById(id));
    }

    /**
     * 批量查询文件（Feign）。
     */
    @PostMapping("/file/list")
    public R<List<MediaFile>> listByIds(@RequestBody List<Long> ids) {
        return R.ok(fileService.listByIds(ids));
    }

    /**
     * 文件管理分页。
     */
    @GetMapping("/file/page")
    public R<PageResult<MediaFile>> page(PageQuery query,
                                         @RequestParam(required = false) String fileName,
                                         @RequestParam(required = false) Integer status) {
        return R.ok(fileService.page(query, fileName, status));
    }
}

