package com.zhixue.ai.controller;

import com.zhixue.ai.domain.entity.KnowledgeDocument;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.annotation.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 知识库管理。
 *
 * <p>修复前没有任何权限校验：只要登录（包括学生）就能往知识库写内容，
 * 而知识库内容会被原样拼进所有学生的答疑提示词。</p>
 */
@Slf4j
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    static final String PERM_KB_EDIT = "ai:kb:edit";
    /** 纯文本，按 UTF-8 读取；过大的文档会占满一次批量向量化 */
    static final long MAX_FILE_BYTES = 1024 * 1024;

    private final KnowledgeBaseService knowledgeBaseService;

    @RequirePermission(PERM_KB_EDIT)
    @PostMapping("/upload")
    public R<KnowledgeDocument> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) String title,
                                       @RequestParam(required = false) Long courseId,
                                       @RequestParam(required = false) String tags) {
        if (file == null || file.isEmpty()) {
            return R.fail(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".txt") && !name.endsWith(".md")) {
            return R.fail(HttpStatus.BAD_REQUEST, "只支持 .txt / .md 纯文本文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            return R.fail(HttpStatus.BAD_REQUEST, "文件不能超过 1MB");
        }
        String docTitle = StringUtils.hasText(title) ? title.trim() : file.getOriginalFilename();
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return R.fail(HttpStatus.BAD_REQUEST, "文档内容不能为空");
            }
            String source = courseId == null ? "manual" : KnowledgeBaseService.COURSE_SOURCE_PREFIX + courseId;
            return R.ok(knowledgeBaseService.createFromText(docTitle, source, tags, text));
        } catch (ServiceException e) {
            return R.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("上传知识库文档失败", e);
            return R.fail("上传失败，请稍后再试");
        }
    }
}
