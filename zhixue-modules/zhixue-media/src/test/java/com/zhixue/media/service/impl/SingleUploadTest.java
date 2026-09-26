package com.zhixue.media.service.impl;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.media.config.MinioConfig.MinioProperties;
import com.zhixue.media.domain.entity.MediaFile;
import com.zhixue.media.mapper.MediaFileMapper;
import com.zhixue.media.service.VideoProcessService;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单文件上传测试。
 *
 * <p>课程发布向导需要上传封面与富文本配图，但后端此前只有分片上传端点，
 * 前端调用的 /media/upload 等 7 个端点均不存在（D13）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SingleUploadTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MediaFileMapper mediaFileMapper;

    @Mock
    private VideoProcessService videoProcessService;

    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        MinioProperties properties = new MinioProperties();
        properties.setBucket("zhixue");
        properties.setChunkPath("chunk");
        properties.setEndpoint("http://127.0.0.1:9000");

        fileService = new FileServiceImpl(minioClient, properties, mediaFileMapper, videoProcessService);
        // stub 模式下不触达 MinIO，便于在无对象存储的环境验证入参校验与落库逻辑
        ReflectionTestUtils.setField(fileService, "storageMode", "stub");
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/png", content);
    }

    @Test
    void shouldRejectEmptyFile() {
        assertThatThrownBy(() -> fileService.uploadSingle(file("a.png", new byte[0]), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文件");
    }

    @Test
    void shouldRejectNullFile() {
        assertThatThrownBy(() -> fileService.uploadSingle(null, null))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void shouldRejectBucketOutsideWhitelist() {
        assertThatThrownBy(() ->
                fileService.uploadSingle(file("a.png", "data".getBytes()), "attacker-bucket"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存储桶");
    }

    @Test
    void shouldSanitizeFileNameWithPathTraversal() {
        MediaFile result = fileService.uploadSingle(
                file("../../../etc/passwd", "data".getBytes()), null);

        assertThat(result.getFileName()).isEqualTo("passwd");
        assertThat(result.getObjectName())
                .as("对象名不得包含路径穿越片段")
                .doesNotContain("..");
    }

    @Test
    void shouldComputeMd5AndPersistRecord() {
        MediaFile result = fileService.uploadSingle(file("cover.png", "hello".getBytes()), null);

        // "hello" 的 MD5
        assertThat(result.getFileMd5()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
        assertThat(result.getFileSize()).isEqualTo(5L);
        assertThat(result.getBucket()).isEqualTo("zhixue");
        assertThat(result.getFileUrl()).contains("zhixue");
        org.mockito.Mockito.verify(mediaFileMapper)
                .insert(org.mockito.ArgumentMatchers.any(MediaFile.class));
    }

    @Test
    void shouldMarkUploadedFileAsAvailable() {
        MediaFile result = fileService.uploadSingle(file("cover.png", "hello".getBytes()), null);

        assertThat(result.getStatus())
                .as("单文件上传完成即可用，状态应为成功")
                .isEqualTo(3);
    }
}
