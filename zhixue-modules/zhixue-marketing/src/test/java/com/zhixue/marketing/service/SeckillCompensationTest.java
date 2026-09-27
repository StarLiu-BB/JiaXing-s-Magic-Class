package com.zhixue.marketing.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 秒杀补偿与 Lua 规范测试。
 *
 * <p>D24：Lua 扣减 Redis 库存成功后若数据库写入失败，
 * @Transactional 只回滚数据库，Redis 的库存与"已抢用户"集合不会回滚，
 * 导致库存永久丢失且该用户被永久标记为已抢购。</p>
 *
 * <p>D26：同一次扣减涉及两个 key（库存、用户集合），
 * 都必须通过 KEYS 传入，否则 Redis Cluster 无法校验槽位一致性，
 * 运行时报 CROSSSLOT 错误。</p>
 */
class SeckillCompensationTest {

    private Path repoRoot() {
        // 从 zhixue-modules/zhixue-marketing 回溯两级
        return Paths.get("").toAbsolutePath().getParent().getParent();
    }

    private String readLua() throws Exception {
        Path lua = repoRoot().resolve(
                "zhixue-modules/zhixue-marketing/src/main/resources/lua/seckill_stock.lua");
        assertThat(Files.exists(lua)).as("Lua 脚本应存在: %s", lua).isTrue();
        return Files.readString(lua, StandardCharsets.UTF_8);
    }

    private String readRollbackLua() throws Exception {
        Path lua = repoRoot().resolve(
                "zhixue-modules/zhixue-marketing/src/main/resources/lua/seckill_rollback.lua");
        assertThat(Files.exists(lua))
                .as("必须提供回滚脚本以补偿数据库写入失败的场景: %s", lua)
                .isTrue();
        return Files.readString(lua, StandardCharsets.UTF_8);
    }

    private String readServiceImpl() throws Exception {
        return Files.readString(repoRoot().resolve(
                "zhixue-modules/zhixue-marketing/src/main/java/com/zhixue/marketing/"
                        + "service/impl/SeckillServiceImpl.java"), StandardCharsets.UTF_8);
    }

    @Test
    void bothKeysMustBePassedViaKeysForClusterCompatibility() throws Exception {
        String lua = readLua();

        assertThat(lua)
                .as("用户集合 key 必须通过 KEYS[2] 传入，"
                        + "否则 Redis Cluster 无法校验槽位，运行时报 CROSSSLOT")
                .contains("KEYS[2]");
        assertThat(lua)
                .as("userKey 不应再从 ARGV 读取")
                .doesNotContain("local userKey = ARGV[1]");
    }

    @Test
    void rollbackScriptMustRestoreStockAndRemoveUser() throws Exception {
        String lua = readRollbackLua();

        assertThat(lua).as("回滚脚本必须归还库存").contains("INCR");
        assertThat(lua).as("回滚脚本必须移除已抢购标记").contains("SREM");
        assertThat(lua).as("回滚脚本同样要用 KEYS 传两个 key").contains("KEYS[2]");
    }

    @Test
    void rollbackMustOnlyRunWhenUserWasActuallyMarked() throws Exception {
        String lua = readRollbackLua();

        // 幂等保护：只有该用户确实在集合中才归还库存，
        // 否则重复调用回滚会把库存越加越多
        assertThat(lua)
                .as("回滚前必须校验用户确实被标记过，避免重复回滚导致库存虚增")
                .contains("SISMEMBER");
    }

    @Test
    void serviceMustCompensateRedisWhenDatabaseWriteFails() throws Exception {
        String impl = readServiceImpl();

        assertThat(impl)
                .as("必须加载回滚脚本")
                .contains("seckill_rollback.lua");
        assertThat(impl)
                .as("数据库写入失败时必须补偿 Redis，否则库存永久丢失")
                .contains("rollbackStock");
    }

    @Test
    void serviceMustPassBothKeysAsKeys() throws Exception {
        String impl = readServiceImpl();

        assertThat(impl)
                .as("执行脚本时应把 stockKey 与 userKey 一起作为 KEYS 传入")
                .contains("List.of(stockKey, userKey)");
        assertThat(impl)
                .as("不应再把 userKey 作为 ARGV 传递")
                .doesNotContain("Collections.singletonList(stockKey), userKey");
    }
}
