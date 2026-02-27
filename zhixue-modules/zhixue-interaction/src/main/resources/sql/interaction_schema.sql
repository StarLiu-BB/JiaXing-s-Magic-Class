CREATE DATABASE IF NOT EXISTS zhixue_interaction DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE zhixue_interaction;

DROP TABLE IF EXISTS danmaku;
CREATE TABLE danmaku
(
    id           BIGINT      NOT NULL PRIMARY KEY,
    room_id      BIGINT      NOT NULL COMMENT '房间ID/课程ID',
    user_id      BIGINT      NOT NULL COMMENT '发送用户ID',
    content      TEXT        NOT NULL COMMENT '弹幕内容',
    time_point   INT         DEFAULT 0 COMMENT '视频时间点(秒)',
    audit_status TINYINT     DEFAULT 0 COMMENT '0待审核 1通过 2拒绝',
    audit_remark VARCHAR(255)         COMMENT '审核备注',
    create_time  DATETIME             COMMENT '创建时间',
    update_time  DATETIME             COMMENT '更新时间',
    deleted      TINYINT     DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_room (room_id),
    INDEX idx_user (user_id),
    INDEX idx_audit (audit_status)
);

-- 课程点赞表
DROP TABLE IF EXISTS interaction_course_like;
CREATE TABLE interaction_course_like
(
    id          BIGINT      NOT NULL PRIMARY KEY,
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    course_id   BIGINT      NOT NULL COMMENT '课程ID',
    create_time DATETIME             COMMENT '创建时间',
    update_time DATETIME             COMMENT '更新时间',
    deleted     TINYINT     DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_user_course (user_id, course_id),
    INDEX idx_course (course_id),
    INDEX idx_user (user_id)
) COMMENT '课程点赞记录表';

-- 课程收藏表
DROP TABLE IF EXISTS interaction_course_favorite;
CREATE TABLE interaction_course_favorite
(
    id          BIGINT      NOT NULL PRIMARY KEY,
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    course_id   BIGINT      NOT NULL COMMENT '课程ID',
    create_time DATETIME             COMMENT '创建时间',
    update_time DATETIME             COMMENT '更新时间',
    deleted     TINYINT     DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_user_course (user_id, course_id),
    INDEX idx_course (course_id),
    INDEX idx_user (user_id)
) COMMENT '课程收藏记录表';

-- 课程互动统计表
DROP TABLE IF EXISTS interaction_course_stats;
CREATE TABLE interaction_course_stats
(
    id             BIGINT      NOT NULL PRIMARY KEY,
    course_id      BIGINT      NOT NULL COMMENT '课程ID',
    like_count     INT         DEFAULT 0 COMMENT '点赞数',
    favorite_count INT         DEFAULT 0 COMMENT '收藏数',
    create_time    DATETIME             COMMENT '创建时间',
    update_time    DATETIME             COMMENT '更新时间',
    deleted        TINYINT     DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_course (course_id)
) COMMENT '课程互动统计表';


