CREATE DATABASE IF NOT EXISTS zhixue_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE zhixue_order;

DROP TABLE IF EXISTS `order`;
CREATE TABLE `order`
(
    id           BIGINT       NOT NULL PRIMARY KEY,
    order_no     VARCHAR(64)  NOT NULL UNIQUE COMMENT '订单号',
    user_id      BIGINT       NOT NULL COMMENT '用户ID',
    product_id   BIGINT       NOT NULL COMMENT '商品ID',
    product_type TINYINT      NOT NULL COMMENT '商品类型：0课程 1其他',
    amount       DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    status       TINYINT      DEFAULT 0 COMMENT '0待支付 1已支付 2已取消 3已退款',
    pay_channel  VARCHAR(32)  COMMENT '支付渠道: wechat/alipay',
    pay_no       VARCHAR(128) COMMENT '第三方支付单号',
    pay_time     DATETIME     COMMENT '支付时间',
    expire_time  DATETIME     COMMENT '超时时间',
    remark       VARCHAR(255) COMMENT '备注',
    create_time  DATETIME,
    update_time  DATETIME,
    deleted      TINYINT      DEFAULT 0,
    INDEX idx_user (user_id),
    INDEX idx_order (order_no),
    INDEX idx_status (status)
);


