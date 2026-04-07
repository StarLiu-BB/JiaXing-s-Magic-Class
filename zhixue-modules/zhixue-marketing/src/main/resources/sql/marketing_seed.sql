USE zhixue_marketing;

DELETE FROM seckill_order;
DELETE FROM seckill_activity;
DELETE FROM coupon_user;
DELETE FROM coupon;

INSERT INTO coupon
    (id, name, total_count, remain_count, discount, threshold_amount, start_time, end_time, status, user_limit, create_time, update_time, deleted)
VALUES
    (61001, '新用户立减券', 500, 480, 30.00, 99.00, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1, 1, NOW(), NOW(), 0),
    (61002, '课程包折扣券', 300, 295, 50.00, 199.00, NOW(), DATE_ADD(NOW(), INTERVAL 15 DAY), 1, 1, NOW(), NOW(), 0);

INSERT INTO coupon_user
    (id, coupon_id, user_id, status, expire_time, create_time, update_time, deleted)
VALUES
    (62001, 61001, 1003, 0, DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW(), 0);

INSERT INTO seckill_activity
    (id, title, product_id, seckill_price, total_stock, start_time, end_time, status, create_time, update_time, deleted)
VALUES
    (63001, '春季课程包秒杀', 2002, 99.00, 50, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 5 HOUR), 1, NOW(), NOW(), 0);
