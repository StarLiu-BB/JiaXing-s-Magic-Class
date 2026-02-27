package com.zhixue.media.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 媒资文件实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("media_file")
public class MediaFile extends BaseEntity {

    private String fileMd5;

    private String fileName;

    private String bucket;

    private String objectName;

    private String fileUrl;

    private String fileType;

    private Long fileSize;

    /**
     * 0-上传中 1-合并完成 2-转码中 3-转码成功 4-失败
     */
    private Integer status;

    private String remark;
}

