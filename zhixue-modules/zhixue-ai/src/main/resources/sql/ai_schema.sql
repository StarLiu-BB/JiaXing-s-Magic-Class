CREATE DATABASE IF NOT EXISTS zhixue_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE zhixue_ai;

DROP TABLE IF EXISTS kb_document;
CREATE TABLE kb_document
(
    id           BIGINT       NOT NULL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    source       VARCHAR(64)  NOT NULL COMMENT '来源，如课程ID/手册等',
    tags         VARCHAR(255)          COMMENT '标签',
    status       TINYINT      DEFAULT 1 COMMENT '1启用 0禁用',
    create_time  DATETIME,
    update_time  DATETIME,
    deleted      TINYINT      DEFAULT 0
);

DROP TABLE IF EXISTS kb_chunk;
CREATE TABLE kb_chunk
(
    id           BIGINT       NOT NULL PRIMARY KEY,
    document_id  BIGINT       NOT NULL,
    chunk_index  INT          NOT NULL,
    content      TEXT         NOT NULL,
    es_id        VARCHAR(128)          COMMENT '在ES中的文档ID',
    create_time  DATETIME,
    update_time  DATETIME,
    deleted      TINYINT      DEFAULT 0,
    INDEX idx_doc (document_id)
);

DROP TABLE IF EXISTS prompt_template;
CREATE TABLE prompt_template
(
    id           BIGINT       NOT NULL PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL UNIQUE COMMENT '模板编码，例如 COURSE_QA',
    name         VARCHAR(128) NOT NULL,
    content      TEXT         NOT NULL COMMENT '模板内容，支持占位符',
    description  VARCHAR(255)          COMMENT '说明',
    create_time  DATETIME,
    update_time  DATETIME,
    deleted      TINYINT      DEFAULT 0
);


