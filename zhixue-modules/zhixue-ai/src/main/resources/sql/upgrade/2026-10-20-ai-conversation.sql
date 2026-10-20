-- 已有数据的环境新增 AI 会话两张表。可重复执行：表已存在时什么都不做。
USE zhixue_ai;

CREATE TABLE IF NOT EXISTS ai_conversation
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

CREATE TABLE IF NOT EXISTS ai_message
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
