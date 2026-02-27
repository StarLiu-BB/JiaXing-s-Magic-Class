package com.zhixue.media.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 合并分片参数。
 */
@Data
public class MergeChunkDTO {

    @NotBlank(message = "文件哈希不能为空")
    private String fileMd5;

    @NotBlank(message = "文件名称不能为空")
    private String fileName;

    private String fileType;

    @NotNull(message = "分片总数不能为空")
    @Min(value = 1, message = "分片总数必须大于0")
    private Integer chunkTotal;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 0, message = "文件大小格式错误")
    private Long fileSize;

    /**
     * 指定桶名称，可选。
     */
    private String bucket;
}

