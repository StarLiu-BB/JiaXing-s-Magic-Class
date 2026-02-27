package com.zhixue.media.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传参数。
 */
@Data
public class ChunkUploadDTO {

    @NotBlank(message = "文件哈希不能为空")
    private String fileMd5;

    @NotNull(message = "分片索引不能为空")
    @Min(value = 0, message = "分片索引从0开始")
    private Integer chunkIndex;

    @NotNull(message = "分片总数不能为空")
    @Min(value = 1, message = "分片总数必须大于0")
    private Integer chunkTotal;

    @NotBlank(message = "文件名称不能为空")
    private String fileName;

    private Long chunkSize;

    /**
     * 指定桶名称，默认使用配置的桶。
     */
    private String bucket;

    @NotNull(message = "分片文件不能为空")
    private MultipartFile chunkFile;
}

