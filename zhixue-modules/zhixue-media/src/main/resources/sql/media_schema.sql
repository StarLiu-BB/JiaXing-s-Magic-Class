CREATE DATABASE IF NOT EXISTS zhixue_media DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE zhixue_media;

CREATE TABLE IF NOT EXISTS media_file
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    file_md5    VARCHAR(64)  NOT NULL COMMENT '文件唯一哈希',
    file_name   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    bucket      VARCHAR(128) NOT NULL COMMENT '存储桶',
    object_name VARCHAR(512) NOT NULL COMMENT '对象路径',
    file_url    VARCHAR(512)          COMMENT '访问地址',
    file_type   VARCHAR(64)           COMMENT 'MIME 类型',
    file_size   BIGINT       DEFAULT 0 COMMENT '文件大小',
    status      TINYINT      DEFAULT 0 COMMENT '0上传中 1合并完成 2转码中 3成功 4失败',
    remark      VARCHAR(255)          COMMENT '备注',
    create_time DATETIME              COMMENT '创建时间',
    update_time DATETIME              COMMENT '更新时间',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_md5 (file_md5),
    INDEX idx_status (status)
);

