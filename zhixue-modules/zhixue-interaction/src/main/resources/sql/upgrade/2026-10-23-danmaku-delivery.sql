USE zhixue_interaction;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'danmaku' AND column_name = 'message_id') = 0,
    'ALTER TABLE danmaku ADD COLUMN message_id VARCHAR(64) NULL COMMENT ''持久化幂等消息ID''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'danmaku' AND index_name = 'uk_danmaku_message') = 0,
    'ALTER TABLE danmaku ADD UNIQUE KEY uk_danmaku_message (message_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
