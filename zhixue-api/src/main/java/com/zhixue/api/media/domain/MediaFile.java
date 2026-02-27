package com.zhixue.api.media.domain;

import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 媒资文件信息。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaFile extends BaseEntity {

    private String fileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private Integer status;
}

