package com.zhixue.interaction.sensitive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * DFA 敏感词过滤。
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    @Value("${interaction.sensitive-words:傻逼,垃圾,色情,赌博,欺诈}")
    private String sensitiveWords;

    /**
     * 敏感词字典树根节点。
     */
    private final Node root = new Node();

    @jakarta.annotation.PostConstruct
    public void init() {
        Arrays.stream(sensitiveWords.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(this::addWord);
    }

    /**
     * 添加敏感词。
     */
    public void addWord(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        Node node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new Node());
        }
        node.end = true;
    }

    /**
     * 过滤敏感词，将命中的部分替换为 *。
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char[] chars = text.toCharArray();
        int n = chars.length;
        for (int i = 0; i < n; i++) {
            Node node = root;
            int j = i;
            int lastMatch = -1;
            while (j < n) {
                char c = chars[j];
                node = node.children.get(c);
                if (node == null) {
                    break;
                }
                if (node.end) {
                    lastMatch = j;
                }
                j++;
            }
            if (lastMatch != -1) {
                for (int k = i; k <= lastMatch; k++) {
                    chars[k] = '*';
                }
                i = lastMatch;
            }
        }
        return new String(chars);
    }

    private static class Node {
        Map<Character, Node> children = new HashMap<>();
        boolean end;
    }
}

