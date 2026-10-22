USE zhixue_order;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'order' AND column_name = 'activity_id') = 0,
    'ALTER TABLE `order` ADD COLUMN activity_id BIGINT NULL COMMENT ''秒杀活动ID，普通订单为NULL''', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP PROCEDURE IF EXISTS migrate_seckill_idempotency;
DELIMITER $$
CREATE PROCEDURE migrate_seckill_idempotency()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'zhixue_marketing' AND table_name = 'seckill_order') THEN
        IF EXISTS (SELECT 1 FROM zhixue_marketing.seckill_order s JOIN `order` o ON BINARY o.order_no = BINARY s.order_no
            WHERE o.user_id != s.user_id OR (o.activity_id IS NOT NULL AND o.activity_id != s.activity_id))
            OR EXISTS (SELECT 1 FROM zhixue_marketing.seckill_order GROUP BY order_no HAVING COUNT(DISTINCT activity_id) > 1) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Seckill history conflicts; reconcile orders before migration';
        END IF;
        UPDATE `order` o JOIN zhixue_marketing.seckill_order s ON BINARY s.order_no = BINARY o.order_no
            SET o.activity_id = s.activity_id WHERE o.activity_id IS NULL;
    END IF;

    UPDATE `order` SET activity_id = CAST(SUBSTRING(remark, 6) AS UNSIGNED)
        WHERE activity_id IS NULL AND remark REGEXP '^秒杀活动 [1-9][0-9]{0,18}$'
        AND CAST(SUBSTRING(remark, 6) AS DECIMAL(20,0)) <= 9223372036854775807;

    IF EXISTS (SELECT 1 FROM `order` WHERE activity_id IS NOT NULL
        GROUP BY activity_id, user_id HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate activity/user orders; reconcile without deleting payment history';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'order' AND index_name = 'uk_order_activity_user') THEN
        ALTER TABLE `order` ADD UNIQUE KEY uk_order_activity_user (activity_id, user_id);
    END IF;
END$$
DELIMITER ;
CALL migrate_seckill_idempotency();
DROP PROCEDURE migrate_seckill_idempotency;
