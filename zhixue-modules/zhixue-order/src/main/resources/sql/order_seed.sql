USE zhixue_order;

DELETE FROM `order`;

INSERT INTO `order`
    (id, order_no, user_id, product_id, product_type, amount, status, pay_channel, pay_no, pay_time, expire_time, remark, create_time, update_time, deleted)
VALUES
    (50001, 'ORD202604070001', 1003, 2001, 0, 199.00, 1, 'sandbox', 'PAY202604070001', NOW(), DATE_ADD(NOW(), INTERVAL 30 MINUTE), '已支付示例订单', NOW(), NOW(), 0),
    (50002, 'ORD202604070002', 1003, 2002, 0, 299.00, 0, 'sandbox', NULL, NULL, DATE_ADD(NOW(), INTERVAL 30 MINUTE), '待支付示例订单', NOW(), NOW(), 0);
