package com.zhixue.media.security;

import com.zhixue.common.core.exception.ServiceException;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 对象存储路径校验工具。
 *
 * <p>fileMd5 / fileName / bucket 均来自客户端请求体，会被拼接成 MinIO 对象名。
 * 若不校验，攻击者可用 {@code ../} 跨目录写入，或指定任意 bucket 覆盖既有对象。</p>
 */
public final class ObjectPathGuard {

    private static final Pattern MD5_PATTERN = Pattern.compile("^[a-fA-F0-9]{32}$");

    /** 对象名中允许的字符，其余一律替换，避免破坏路径结构或触发 SDK 编码问题。 */
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]");

    private static final int MAX_FILENAME_LENGTH = 120;

    private ObjectPathGuard() {
    }

    /**
     * 校验文件哈希必须是标准 32 位 MD5，杜绝借哈希字段做路径穿越。
     */
    public static String requireValidMd5(String fileMd5) {
        if (!StringUtils.hasText(fileMd5) || !MD5_PATTERN.matcher(fileMd5.trim()).matches()) {
            throw new ServiceException("文件哈希格式非法，必须是 32 位 MD5");
        }
        return fileMd5.trim().toLowerCase();
    }

    /**
     * 规整文件名：只保留最后一段（丢弃任何目录部分），并过滤不安全字符。
     */
    public static String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new ServiceException("文件名不能为空");
        }
        // 统一分隔符后取末段，"../../etc/passwd" -> "passwd"
        String normalized = fileName.replace('\\', '/');
        String baseName = Paths.get(normalized).getFileName() == null
                ? "" : Paths.get(normalized).getFileName().toString();

        baseName = baseName.replace("..", "");
        baseName = UNSAFE_FILENAME_CHARS.matcher(baseName).replaceAll("_");
        baseName = baseName.replaceAll("^[._]+", "");

        if (!StringUtils.hasText(baseName)) {
            throw new ServiceException("文件名非法");
        }
        if (baseName.length() > MAX_FILENAME_LENGTH) {
            baseName = baseName.substring(baseName.length() - MAX_FILENAME_LENGTH);
        }
        return baseName;
    }

    /**
     * 解析目标桶：客户端可不传（用默认桶），但不得指定默认桶以外的任意桶。
     *
     * @param requested     客户端请求的桶名，可为空
     * @param defaultBucket 配置中的默认桶
     */
    public static String resolveBucket(String requested, String defaultBucket) {
        if (!StringUtils.hasText(defaultBucket)) {
            throw new ServiceException("默认存储桶未配置");
        }
        if (!StringUtils.hasText(requested)) {
            return defaultBucket;
        }
        if (!defaultBucket.equals(requested.trim())) {
            throw new ServiceException("不允许使用的存储桶：" + requested);
        }
        return defaultBucket;
    }
}
