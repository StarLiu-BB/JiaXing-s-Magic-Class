package com.zhixue.media.controller;

import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import com.zhixue.media.domain.entity.MediaFile;
import com.zhixue.media.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 媒资接口访问控制。
 *
 * <p>修复前：查询接口只要求登录。课时视频经媒资上传后，fileUrl 会出现在 /media/file/page 中，
 * 任何学生都能翻出全部文件地址，绕开课时的购买校验；上传接口同样只要求登录，
 * 学生可以往对象存储写任意文件。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaAccessControlTest {

    private static final long STUDENT = 1003L;

    @Mock
    private FileService fileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new UploadController(fileService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new SecurityAccessAspect());
        mockMvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(fileService.page(any(), any(), any())).thenReturn(PageResult.of(List.of(), 0L, 10L));
        when(fileService.uploadSingle(any(), any())).thenReturn(new MediaFile());
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, long userId, String... perms) {
        return builder
                .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, String.valueOf(userId))
                .header(InternalAccessConstants.USER_PERMISSIONS_HEADER, String.join(",", perms));
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void studentCannotBrowseOrLookUpMedia() throws Exception {
        mockMvc.perform(asUser(get("/media/file/page"), STUDENT)).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(asUser(get("/media/file/{id}", 1L), STUDENT)).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(asUser(post("/media/file/list"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON).content("[1,2]"))
                .andExpect(jsonPath("$.code").value(403));

        verify(fileService, never()).page(any(), any(), any());
        verify(fileService, never()).getById(anyLong());
        verify(fileService, never()).listByIds(any());
    }

    @Test
    void operatorWithMediaPermissionCanBrowse() throws Exception {
        mockMvc.perform(asUser(get("/media/file/page"), 1001L, "media:file:list"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void studentCannotUpload() throws Exception {
        mockMvc.perform(asUser(multipart("/media/upload").file(png()), STUDENT))
                .andExpect(jsonPath("$.code").value(403));
        verify(fileService, never()).uploadSingle(any(), any());
    }

    @Test
    void courseEditorsCanUpload() throws Exception {
        // 上传仅用于课程发布页的封面等素材：持有课程编辑或发布权限的人可以上传
        mockMvc.perform(asUser(multipart("/media/upload").file(png()), 1002L, "course:edit"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(asUser(multipart("/media/upload").file(png()), 1002L, "course:publish"))
                .andExpect(jsonPath("$.code").value(200));
    }
}
