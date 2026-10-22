USE zhixue_ai;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_message' AND column_name = 'notice') = 0,
    'ALTER TABLE ai_message ADD COLUMN notice VARCHAR(500) NULL COMMENT ''当次检索降级提示''', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_message' AND column_name = 'trace') = 0,
    'ALTER TABLE ai_message ADD COLUMN trace JSON NULL COMMENT ''生成版本及检索快照，不含凭据''', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
