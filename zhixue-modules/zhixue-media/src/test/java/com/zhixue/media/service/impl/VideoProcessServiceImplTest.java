package com.zhixue.media.service.impl;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.media.domain.entity.MediaFile;
import com.zhixue.media.strategy.VideoProcessStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoProcessServiceImplTest {

    @Test
    void shouldReturnOriginalObjectNameWhenNoStrategyConfigured() {
        VideoProcessServiceImpl service = new VideoProcessServiceImpl(List.of());
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(1L);
        mediaFile.setObjectName("upload/a.mp4");
        mediaFile.setFileType("video/mp4");

        String result = service.transcode(mediaFile);

        assertThat(result).isEqualTo("upload/a.mp4");
    }

    @Test
    void shouldReturnOriginalObjectNameWhenNoStrategySupportsFileType() {
        VideoProcessStrategy strategy = mock(VideoProcessStrategy.class);
        when(strategy.supports("video/mp4")).thenReturn(false);
        VideoProcessServiceImpl service = new VideoProcessServiceImpl(List.of(strategy));
        MediaFile mediaFile = new MediaFile();
        mediaFile.setObjectName("upload/raw.mp4");
        mediaFile.setFileType("video/mp4");

        String result = service.transcode(mediaFile);

        assertThat(result).isEqualTo("upload/raw.mp4");
    }

    @Test
    void shouldThrowServiceExceptionWhenStrategyFails() throws Exception {
        VideoProcessStrategy strategy = mock(VideoProcessStrategy.class);
        when(strategy.supports("video/mp4")).thenReturn(true);
        when(strategy.transcode(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("ffmpeg error"));
        VideoProcessServiceImpl service = new VideoProcessServiceImpl(List.of(strategy));
        MediaFile mediaFile = new MediaFile();
        mediaFile.setObjectName("upload/raw.mp4");
        mediaFile.setFileType("video/mp4");

        assertThatThrownBy(() -> service.transcode(mediaFile))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("转码失败");
    }
}
