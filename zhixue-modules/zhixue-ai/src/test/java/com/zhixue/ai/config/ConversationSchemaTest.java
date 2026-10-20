package com.zhixue.ai.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 新表在全新环境（ai_schema.sql）与已有数据环境（增量脚本）都要能建出来，且增量脚本绝不清库 */
class ConversationSchemaTest {

    @Test
    void schemaAndUpgradeBothCreateConversationTables() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/sql/ai_schema.sql"), StandardCharsets.UTF_8);
        String upgrade = Files.readString(Path.of("src/main/resources/sql/upgrade/2026-10-20-ai-conversation.sql"),
                StandardCharsets.UTF_8);
        for (String table : new String[]{"ai_conversation", "ai_message"}) {
            assertThat(schema).contains("CREATE TABLE " + table);
            assertThat(upgrade).contains("CREATE TABLE IF NOT EXISTS " + table);
        }
        for (String sql : new String[]{schema, upgrade}) {
            assertThat(sql).contains("idx_user_recent (user_id, deleted, last_message_time)")
                    .contains("idx_conversation (conversation_id, id)");
        }
        assertThat(upgrade.toLowerCase()).doesNotContain("drop table").doesNotContain("delete from")
                .doesNotContain("truncate");
    }
}
