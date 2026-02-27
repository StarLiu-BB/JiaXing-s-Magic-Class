CREATE DATABASE IF NOT EXISTS zhixue_marketing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE zhixue_marketing;

DROP TABLE IF EXISTS seckill_activity;
CREATE TABLE seckill_activity
(
    id           BIGINT       NOT NULL PRIMARY KEY,
    title        VARCHAR(128) NOT NULL,
    product_id   BIGINT       NOT NULL,
    seckill_price DECIMAL(10,2) NOT NULL,
    total_stock  INT          NOT NULL,
    start_time   DATETIME     NOT NULL,
    end_time     DATETIME     NOT NULL,
    status       TINYINT      DEFAULT 0 COMMENT '0未开始 1进行中 2已结束',
    create_time  DATETIME,
    update_time  DATETIME,
    deleted      TINYINT      DEFAULT 0,
    INDEX idx_status (status),
    INDEX idx_time (start_time, end_time)
);

DROP TABLE IF EXISTS seckill_order;
CREATE TABLE seckill_order
(
    id           BIGINT      NOT NULL PRIMARY KEY,
    activity_id  BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    order_no     VARCHAR(64) NOT NULL,
    status       TINYINT     DEFAULT 0 COMMENT '0已锁定 1已下单 2已取消',
    create_time  DATETIME,
    update_time  DATETIME,
    deleted      TINYINT     DEFAULT 0,
    UNIQUE KEY uniq_activity_user (activity_id, user_id)
);

DROP TABLE IF EXISTS coupon;
CREATE TABLE coupon
(
    id             BIGINT       NOT NULL PRIMARY KEY,
    name           VARCHAR(128) NOT NULL,
    total_count    INT          NOT NULL,
    remain_count   INT          NOT NULL,
    discount       DECIMAL(10,2) DEFAULT 0,
    threshold_amount DECIMAL(10,2) DEFAULT 0,
    start_time     DATETIME     NOT NULL,
    end_time       DATETIME     NOT NULL,
    status         TINYINT      DEFAULT 0 COMMENT '0未开始 1生效 2结束',
    user_limit     INT          DEFAULT 1 COMMENT '每人限领',
    create_time    DATETIME,
    update_time    DATETIME,
    deleted        TINYINT      DEFAULT 0
);

DROP TABLE IF EXISTS coupon_user;
CREATE TABLE coupon_user
(
    id           BIGINT      NOT NULL PRIMARY KEY,
    coupon_id    BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    status       TINYINT     DEFAULT 0 COMMENT '0未使用 1已使用 2已过期',
    expire_time  DATETIME,
    create_time  DATETIME,
    update_time  DATETIME,
    deleted      TINYINT     DEFAULT 0,
    UNIQUE KEY uniq_coupon_user (coupon_id, user_id)
);


