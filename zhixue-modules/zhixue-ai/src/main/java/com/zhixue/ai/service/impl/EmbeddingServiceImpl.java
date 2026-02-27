package com.zhixue.ai.service.impl;

import com.zhixue.ai.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 文字转数字向量的服务实现类。
 * 这个类实现了把文字转换成数字向量的功能。
 * 目前使用哈希算法模拟向量，实际项目中应该替换成真正的向量模型接口。
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    /**
     * 向量的维度，固定为 128 维
     */
    private static final int DIM = 128;

    /**
     * 把文字转换成数字向量。
     * 使用哈希算法生成一个 128 维的向量，每维的值在 0 到 1 之间。
     * 注意：这只是模拟实现，实际项目应该调用专业的向量模型接口。
     * @param text 需要转换的文字
     * @return 转换后的数字向量列表
     */
    @Override
    public List<Float> embed(String text) {
        try {
            // 使用 SHA-256 哈希算法生成向量
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            List<Float> vec = new ArrayList<>(DIM);
            for (int i = 0; i < DIM; i++) {
                int idx = i % hash.length;
                // 把字节数值转换成 0 到 1 之间的小数
                vec.add((hash[idx] & 0xff) / 255.0f);
            }
            return vec;
        } catch (Exception e) {
            log.error("计算 embedding 失败", e);
            return List.of();
        }
    }
}


