package com.zhixue.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 本地大模型（Ollama）配置。
 *
 * <p>此前对接阿里云 DashScope，需要外部 API Key 且按量计费；现改为本机运行的 Ollama，
 * 对话/审核用 qwen3.5:9b，向量化用 qwen3-embedding:0.6b（1024 维）。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.llm")
public class LlmConfig {

    /** Ollama 地址。容器内访问宿主机用 http://host.docker.internal:11434 */
    private String baseUrl = "http://127.0.0.1:11434";

    /** 对话与审核模型 */
    private String chatModel = "qwen3.5:9b";

    /** 向量模型；输出维度必须等于 embeddingDimension */
    private String embeddingModel = "qwen3-embedding:0.6b";

    /** 必须与 ES dense_vector 的 dims 一致，否则写入会被 ES 拒绝；变更后启动时自动重建索引 */
    private int embeddingDimension = 1024;

    /** 答疑生成较长回答，冷启动加载模型也计入，给足时间 */
    private int chatTimeoutSeconds = 90;

    /** 审核只输出很短的 JSON，超时应远小于答疑 */
    private int auditTimeoutSeconds = 20;

    private int embeddingTimeoutSeconds = 30;

    /** 模型在内存中的保留时长，避免每次请求都重新加载 */
    private String keepAlive = "30m";

    /** 上下文窗口；RAG 提示词约 3k token，留出余量 */
    private int numCtx = 8192;
}
