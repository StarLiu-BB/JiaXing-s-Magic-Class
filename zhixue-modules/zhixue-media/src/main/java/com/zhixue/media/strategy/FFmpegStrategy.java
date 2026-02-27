package com.zhixue.media.strategy;

import com.zhixue.media.config.MinioConfig.MinioProperties;
import com.zhixue.media.domain.entity.MediaFile;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 基于 FFmpeg 的视频转码实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FFmpegStrategy implements VideoProcessStrategy {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Value("${media.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Override
    public boolean supports(String fileType) {
        return StringUtils.hasText(fileType) && fileType.toLowerCase().startsWith("video");
    }

    @Override
    public String transcode(MediaFile mediaFile) throws Exception {
        Path source = Files.createTempFile("media-src-", ".tmp");
        Path target = Files.createTempFile("media-target-", ".mp4");
        try {
            try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(mediaFile.getBucket())
                .object(mediaFile.getObjectName())
                .build())) {
            Files.copy(in, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-i", source.toString(),
                "-vcodec", "libx264",
                "-acodec", "aac",
                "-y",
                target.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int code = process.waitFor();
        if (code != 0) {
            String error;
            try (java.io.InputStream errorStream = process.getErrorStream()) {
                error = new String(errorStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            log.error("FFmpeg 转码失败 code={} error={}", code, error);
            throw new IllegalStateException("FFmpeg 转码失败：" + error);
        }

        String objectName = buildTargetObject(mediaFile.getFileMd5(), mediaFile.getFileName());
        try (InputStream upload = Files.newInputStream(target)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(upload, Files.size(target), -1)
                    .contentType("video/mp4")
                    .build());
        }
            return objectName;
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
        }
    }

    private String buildTargetObject(String md5, String fileName) {
        String name = StringUtils.hasText(fileName) ? fileName : md5;
        if (!name.toLowerCase().endsWith(".mp4")) {
            name = name + ".mp4";
        }
        return "transcode/" + md5 + "/" + name;
    }
}

