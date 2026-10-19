package com.zhixue.ai.service;

import java.util.List;

/**
 * 文本向量化服务。
 */
public interface EmbeddingService {

    /**
     * 单条文本向量化。
     *
     * @return 向量；取不到时返回空列表（sandbox）或抛出异常（real），绝不伪造
     */
    List<Float> embed(String text);

    /**
     * 批量向量化，结果顺序与输入一致。
     *
     * @return 与输入等长的向量列表；取不到时返回空列表（sandbox）或抛出异常（real）
     */
    List<List<Float>> embedBatch(List<String> texts);
}
