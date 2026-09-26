package com.zhixue.media.security;

import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对象存储路径校验测试。
 *
 * <p>覆盖 C7：fileMd5 / fileName / bucket 来自客户端且原先只有 @NotBlank，
 * 被直接拼接成 MinIO 对象名，可构造 {@code ../} 跨目录写入或覆盖他人对象。</p>
 */
class ObjectPathGuardTest {

    @Test
    void shouldAcceptValidMd5() {
        assertThatCode(() -> ObjectPathGuard.requireValidMd5("d41d8cd98f00b204e9800998ecf8427e"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptUppercaseMd5IgnoringCase() {
        assertThatCode(() -> ObjectPathGuard.requireValidMd5("D41D8CD98F00B204E9800998ECF8427E"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMd5WithPathTraversal() {
        assertThatThrownBy(() -> ObjectPathGuard.requireValidMd5("../../etc/passwd"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件哈希");
    }

    @Test
    void shouldRejectMd5WithWrongLength() {
        assertThatThrownBy(() -> ObjectPathGuard.requireValidMd5("abc123"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void shouldRejectMd5WithSlash() {
        assertThatThrownBy(() -> ObjectPathGuard.requireValidMd5("d41d8cd98f00b204e9800998ecf8427/"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void shouldRejectBlankMd5() {
        assertThatThrownBy(() -> ObjectPathGuard.requireValidMd5("  "))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void shouldSanitizeFileNameKeepingExtension() {
        String safe = ObjectPathGuard.sanitizeFileName("lesson-01.mp4");
        assertThatCode(() -> ObjectPathGuard.sanitizeFileName("lesson-01.mp4"))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(safe).isEqualTo("lesson-01.mp4");
    }

    @Test
    void shouldStripDirectoryComponentsFromFileName() {
        // 只取文件名部分，杜绝 ../ 与绝对路径写入
        org.assertj.core.api.Assertions
                .assertThat(ObjectPathGuard.sanitizeFileName("../../../etc/passwd"))
                .isEqualTo("passwd");
        org.assertj.core.api.Assertions
                .assertThat(ObjectPathGuard.sanitizeFileName("/var/www/shell.jsp"))
                .isEqualTo("shell.jsp");
    }

    @Test
    void shouldReplaceUnsafeCharactersInFileName() {
        org.assertj.core.api.Assertions
                .assertThat(ObjectPathGuard.sanitizeFileName("a b*c?d.mp4"))
                .doesNotContain(" ", "*", "?");
    }

    @Test
    void shouldRejectFileNameThatBecomesEmptyAfterSanitizing() {
        assertThatThrownBy(() -> ObjectPathGuard.sanitizeFileName("../../"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件名");
    }

    @Test
    void shouldRejectBucketNotInWhitelist() {
        // bucket 由客户端指定时，只允许配置中声明的默认桶，
        // 否则可写入任意桶并覆盖其中对象
        assertThatThrownBy(() -> ObjectPathGuard.resolveBucket("attacker-bucket", "zhixue"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存储桶");
    }

    @Test
    void shouldFallBackToDefaultBucketWhenBlank() {
        org.assertj.core.api.Assertions
                .assertThat(ObjectPathGuard.resolveBucket(null, "zhixue"))
                .isEqualTo("zhixue");
        org.assertj.core.api.Assertions
                .assertThat(ObjectPathGuard.resolveBucket("  ", "zhixue"))
                .isEqualTo("zhixue");
    }

    @Test
    void shouldAcceptDefaultBucketExplicitly() {
        org.assertj.core.api.Assertions
                .assertThat(ObjectPathGuard.resolveBucket("zhixue", "zhixue"))
                .isEqualTo("zhixue");
    }
}
