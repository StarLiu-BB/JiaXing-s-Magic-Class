package com.zhixue.ai.service;

import java.util.List;

/**
 * 文字转数字向量的服务接口。
 * 这个接口定义了把文字转换成数字向量的方法。
 * 数字向量可以用来计算文字之间的相似度，方便在知识库中搜索相关内容。
 */
public interface EmbeddingService {

    /**
     * 把一段文字转换成数字向量。
     * 数字向量是一串数字，可以代表这段文字的含义。
     * @param text 需要转换的文字
     * @return 转换后的数字向量列表
     */
    List<Float> embed(String text);
}


