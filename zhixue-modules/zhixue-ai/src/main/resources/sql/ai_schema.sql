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



DROP TABLE IF EXISTS ai_conversation;
CREATE TABLE ai_conversation
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL COMMENT '所属学生，所有查询必须带此条件',
    title             VARCHAR(64)  NOT NULL COMMENT '首个问题前 20 字，可重命名',
    course_id         BIGINT                COMMENT '限定课程提问时的课程，创建会话时确定',
    last_message_time DATETIME     NOT NULL,
    create_time       DATETIME,
    update_time       DATETIME,
    deleted           TINYINT      DEFAULT 0,
    INDEX idx_user_recent (user_id, deleted, last_message_time)
);

DROP TABLE IF EXISTS ai_message;
CREATE TABLE ai_message
(
    id               BIGINT       NOT NULL PRIMARY KEY,
    conversation_id  BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL COMMENT '冗余：按消息查询时校验归属',
    role             VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
    content          MEDIUMTEXT   NOT NULL,
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1 完成 2 已停止 3 失败',
    sources          JSON                  COMMENT '引用资料标题',
    recommendations  JSON                  COMMENT '[{courseId, score, lessonIds}]，只存 ID 与分数',
    cost_ms          INT                   COMMENT '生成耗时（毫秒）',
    create_time      DATETIME,
    update_time      DATETIME,
    deleted          TINYINT      DEFAULT 0,
    INDEX idx_conversation (conversation_id, id)
);
