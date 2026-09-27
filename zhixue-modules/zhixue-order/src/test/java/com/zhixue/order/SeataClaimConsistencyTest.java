package com.zhixue.order;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seata 声明一致性测试。
 *
 * <p>覆盖 D23：项目声明使用 Seata AT 模式，但：</p>
 * <ul>
 *   <li>没有 undo_log 表 —— AT 模式回滚必然失败</li>
 *   <li>没有 DataSourceProxy —— 数据源未被代理，无法记录回滚日志</li>
 *   <li>@GlobalTransactional 标注的 createOrder 内没有任何跨服务调用，
 *       全局事务退化为本地事务</li>
 * </ul>
 *
 * <p>三者叠加意味着该注解只提供了"支持分布式事务"的假象，
 * 真正发生跨服务不一致时反而会让人误以为有保护。</p>
 *
 * <p>本测试锁定：要么真正具备回滚能力（有 undo_log），
 * 要么不作此声明（无 @GlobalTransactional）。不允许中间状态。</p>
 */
class SeataClaimConsistencyTest {

    private Path repoRoot() {
        return Paths.get("").toAbsolutePath().getParent().getParent();
    }

    private String orderServiceImpl() throws Exception {
        return Files.readString(repoRoot().resolve(
                "zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/"
                        + "service/impl/OrderServiceImpl.java"), StandardCharsets.UTF_8);
    }

    @Test
    void globalTransactionalMustNotBeUsedWithoutUndoLog() throws Exception {
        boolean hasUndoLog = Files.readString(repoRoot().resolve(
                        "zhixue-modules/zhixue-order/src/main/resources/sql/order_schema.sql"),
                StandardCharsets.UTF_8).contains("undo_log");

        String impl = orderServiceImpl();
        // 只统计真实注解使用（行首缩进后紧跟注解），忽略注释中的说明文字
        boolean usesGlobalTransactional = impl.lines()
                .map(String::trim)
                .anyMatch(line -> line.startsWith("@GlobalTransactional"));

        assertThat(usesGlobalTransactional && !hasUndoLog)
                .as("使用 @GlobalTransactional 但缺少 undo_log 表："
                        + "AT 模式回滚必然失败，这是比不用分布式事务更危险的假象。"
                        + "要么补齐 undo_log + DataSourceProxy，要么移除该注解")
                .isFalse();
    }

    @Test
    void localOnlyTransactionShouldUseLocalTransactionAnnotation() throws Exception {
        String impl = orderServiceImpl();

        // createOrder 目前只操作本库 + 发 MQ，没有跨服务 RPC，
        // 用本地事务语义表达即可，避免误导读者
        assertThat(impl)
                .as("createOrder 应使用本地 @Transactional")
                .contains("@Transactional(rollbackFor = Exception.class)\n    public Order createOrder");
    }

    @Test
    void seataDependencyShouldNotBeDeclaredWhenUnused() throws Exception {
        String pom = Files.readString(repoRoot().resolve("zhixue-modules/zhixue-order/pom.xml"),
                StandardCharsets.UTF_8);
        String impl = orderServiceImpl();

        boolean usesGlobalTransactional = impl.lines()
                .map(String::trim)
                .anyMatch(line -> line.startsWith("@GlobalTransactional"));
        if (!usesGlobalTransactional) {
            assertThat(pom)
                    .as("已不使用全局事务时不应保留 seata 依赖，"
                            + "否则后续开发者会误以为分布式事务可用")
                    .doesNotContain("seata-spring-boot-starter");
        }
    }
}
