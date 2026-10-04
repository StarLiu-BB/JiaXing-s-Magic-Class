package com.zhixue.media.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequirePermission;
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
 *
 * <p>修复前所有接口只要求登录：学生能翻出全部媒资的 fileUrl（课时视频经此上传后即绕开购买校验），
 * 也能往对象存储写任意文件。现在上传只对课程内容编辑者开放，查询需媒资管理权限。</p>
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class UploadController {

    /** 媒资只用于课程内容（封面、课时视频等），因此上传沿用课程编辑/发布权限 */
    static final String PERM_COURSE_EDIT = "course:edit";
    static final String PERM_COURSE_PUBLISH = "course:publish";
    /** 查询会暴露 fileUrl，仅媒资管理员可用 */
    static final String PERM_MEDIA_LIST = "media:file:list";

    private final FileService fileService;

    /**
     * 单文件上传（封面、富文本配图等小文件）。
     * 大文件仍走 /upload/chunk + /upload/merge 分片流程。
     */
    @RequirePermission({PERM_COURSE_EDIT, PERM_COURSE_PUBLISH})
    @PostMapping("/upload")
    public R<MediaFile> upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) String bucket) {
        return R.ok(fileService.uploadSingle(file, bucket));
    }

    /**
     * 分片上传。
     */
    @RequirePermission({PERM_COURSE_EDIT, PERM_COURSE_PUBLISH})
    @PostMapping("/upload/chunk")
    public R<Void> uploadChunk(@Valid @ModelAttribute ChunkUploadDTO dto) {
        return fileService.uploadChunk(dto) ? R.ok() : R.fail("上传失败");
    }

    /**
     * 合并分片。
     */
    @RequirePermission({PERM_COURSE_EDIT, PERM_COURSE_PUBLISH})
    @PostMapping("/upload/merge")
    public R<MediaFile> merge(@Valid @RequestBody MergeChunkDTO dto) {
        return R.ok(fileService.mergeChunks(dto));
    }

    /**
     * 获取文件详情。
     */
    @RequirePermission(PERM_MEDIA_LIST)
    @GetMapping("/file/{id}")
    public R<MediaFile> getFile(@PathVariable Long id) {
        return R.ok(fileService.getById(id));
    }

    /**
     * 批量查询文件。
     */
    @RequirePermission(PERM_MEDIA_LIST)
    @PostMapping("/file/list")
    public R<List<MediaFile>> listByIds(@RequestBody List<Long> ids) {
        return R.ok(fileService.listByIds(ids));
    }

    /**
     * 文件管理分页。
     */
    @RequirePermission(PERM_MEDIA_LIST)
    @GetMapping("/file/page")
    public R<PageResult<MediaFile>> page(PageQuery query,
                                         @RequestParam(required = false) String fileName,
                                         @RequestParam(required = false) Integer status) {
        return R.ok(fileService.page(query, fileName, status));
    }
}
