package com.zhixue.ai.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库索引初始化测试。
 *
 * <p>覆盖 D34 的第二半：全仓库没有任何 {@code dense_vector} 映射定义。
 * 首次写入时 ES 会把 vector 字段动态映射为普通 {@code float[]}，
 * 随后的 {@code knn} 查询报错并被 catch 后静默降级为词法匹配 ——
 * 所谓"向量检索"从未真正执行过。</p>
 */
class KnowledgeIndexInitializerTest {

    private Path mainJava() {
        return Paths.get("src/main/java/com/zhixue/ai");
    }

    private String initializerSource() throws Exception {
        Path path = mainJava().resolve("config/KnowledgeIndexInitializer.java");
        assertThat(Files.exists(path))
                .as("缺少索引初始化器，knn 查询会因字段类型不对而始终失败: %s", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void mustDeclareDenseVectorMapping() throws Exception {
        String source = initializerSource();

        assertThat(source)
                .as("vector 字段必须声明为 dense_vector，否则 knn 查询不可用")
                .contains("dense_vector");
        assertThat(source)
                .as("必须开启索引以支持 knn 检索")
                .contains("\"index\", true");
    }

    @Test
    void mustUseCosineSimilarityForTextEmbedding() throws Exception {
        // 文本向量检索的常规选择；与相似度计算方式必须显式声明，
        // 否则默认值可能与业务预期不一致
        assertThat(initializerSource()).contains("cosine");
    }

    @Test
    void dimensionMustComeFromConfigurationNotHardcoded() throws Exception {
        String source = initializerSource();

        assertThat(source)
                .as("维度必须与 EmbeddingService 使用同一配置项，避免两处不一致导致写入被拒")
                .contains("ai.dashscope.embedding.dimension");
    }

    @Test
    void mustNotRecreateExistingIndex() throws Exception {
        String source = initializerSource();

        assertThat(source)
                .as("已存在时不得重建，否则每次重启都会清空知识库")
                .containsAnyOf("HEAD", "exists");
    }

    @Test
    void initializationFailureMustNotBlockStartup() throws Exception {
        String source = initializerSource();

        // ES 是可选依赖（sandbox/stub 模式下可能未部署），
        // 索引创建失败只应告警，不应让整个 AI 服务起不来
        assertThat(source).contains("catch");
    }
}
