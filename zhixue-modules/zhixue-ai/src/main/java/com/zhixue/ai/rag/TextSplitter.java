package com.zhixue.ai.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分片器：按照固定长度+重叠切分，便于向量检索。
 */
@Component
public class TextSplitter {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 100;

    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int len = text.length();
        int start = 0;
        while (start < len) {
            int end = Math.min(start + CHUNK_SIZE, len);
            chunks.add(text.substring(start, end));
            if (end == len) {
                break;
            }
            start = end - CHUNK_OVERLAP;
        }
        return chunks;
    }
}


