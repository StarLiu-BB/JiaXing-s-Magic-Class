package com.zhixue.ai.controller;

import com.zhixue.ai.domain.entity.KnowledgeDocument;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.common.core.domain.R;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理控制器。
 * 这个类提供知识库的管理接口，主要是上传文档的功能。
 * 上传的文档会被切成小段，然后存入知识库，方便 AI 查询时使用。
 */
@Slf4j
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 上传文档到知识库。
     * 接收文本文件，把文件内容切成小段，然后存入知识库。
     * 这样 AI 在回答问题时，就可以从知识库中找到相关内容来参考。
     * @param file 上传的文件，目前支持 TXT 文本文件
     * @param title 文档标题，不能为空
     * @param source 文档来源，比如 manual 表示手动上传
     * @param tags 文档标签，用于分类和检索，可选参数
     * @return 存入知识库后的文档信息
     */
    @PostMapping("/upload")
    public R<KnowledgeDocument> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam @NotBlank String title,
                                       @RequestParam(defaultValue = "manual") String source,
                                       @RequestParam(required = false) String tags) {
        try {
            // 读取文件内容
            String text = new String(file.getBytes());
            // 把文档内容存入知识库，会自动切成小段
            KnowledgeDocument doc = knowledgeBaseService.createFromText(title, source, tags, text);
            return R.ok(doc);
        } catch (Exception e) {
            log.error("上传文档失败", e);
            return R.fail("上传失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
        }
    }
}


