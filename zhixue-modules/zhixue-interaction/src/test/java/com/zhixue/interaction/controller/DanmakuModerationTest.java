package com.zhixue.interaction.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import com.zhixue.interaction.domain.entity.Danmaku;
import com.zhixue.interaction.mapper.DanmakuMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 弹幕审核。
 *
 * <p>修复前实测：学生令牌调用 /danmaku/page 返回 200，可翻看全站弹幕（含被 AI 拒绝的内容、
 * 发送者 userId）；且没有任何人工审核接口，管理端"通过/拒绝"按钮只能写死禁用。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DanmakuModerationTest {

    private static final String LIST = "interaction:danmaku:list";
    private static final String AUDIT = "interaction:danmaku:audit";

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Danmaku.class);
    }

    @Mock
    private DanmakuMapper danmakuMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new DanmakuController(danmakuMapper));
        factory.setProxyTargetClass(true);
        factory.addAspect(new SecurityAccessAspect());
        mockMvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(danmakuMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0, Page.class));
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, long userId, String... perms) {
        return builder
                .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, String.valueOf(userId))
                .header(InternalAccessConstants.USER_PERMISSIONS_HEADER, String.join(",", perms));
    }

    private Danmaku stored(long id, int status) {
        Danmaku d = new Danmaku();
        d.setId(id);
        d.setRoomId(2004L);
        d.setUserId(1003L);
        d.setContent("老师讲得好");
        d.setAuditStatus(status);
        return d;
    }

    @Test
    void studentCannotBrowseAllDanmaku() throws Exception {
        mockMvc.perform(asUser(get("/danmaku/page"), 1003L))
                .andExpect(jsonPath("$.code").value(403));
        verify(danmakuMapper, never()).selectPage(any(), any());
    }

    @Test
    void operatorCanBrowse() throws Exception {
        mockMvc.perform(asUser(get("/danmaku/page"), 1001L, LIST).param("auditStatus", "2"))
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 播放页（任意登录用户，登录由网关校验）不需要审核权限即可拉取已通过的历史弹幕 */
    @Test
    void historyNeedsNoModerationPermission() throws Exception {
        when(danmakuMapper.selectList(any())).thenReturn(List.of());
        mockMvc.perform(get("/danmaku/history").param("roomId", "2004"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void auditRequiresAuditPermission() throws Exception {
        mockMvc.perform(asUser(put("/danmaku/{id}/audit", 7L), 1001L, LIST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":2,\"auditRemark\":\"广告\"}"))
                .andExpect(jsonPath("$.code").value(403));
        verify(danmakuMapper, never()).update(org.mockito.ArgumentMatchers.<Danmaku>any(), any());
    }

    /** 断言真正下发的 SET 子句，而不是实体上的字段值（updateById 会静默跳过 null 字段） */
    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<Danmaku> capturedUpdate() {
        ArgumentCaptor<Wrapper<Danmaku>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(danmakuMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        return (LambdaUpdateWrapper<Danmaku>) captor.getValue();
    }

    @Test
    void rejectStoresStatusAndReason() throws Exception {
        when(danmakuMapper.selectById(7L)).thenReturn(stored(7L, 1));
        when(danmakuMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);

        mockMvc.perform(asUser(put("/danmaku/{id}/audit", 7L), 1001L, AUDIT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":2,\"auditRemark\":\"广告引流\"}"))
                .andExpect(jsonPath("$.code").value(200));

        LambdaUpdateWrapper<Danmaku> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("audit_status=").contains("audit_remark=");
        assertThat(update.getParamNameValuePairs().values()).contains(2, "广告引流");
    }

    @Test
    void rejectWithoutReasonIsRefused() throws Exception {
        when(danmakuMapper.selectById(7L)).thenReturn(stored(7L, 1));

        mockMvc.perform(asUser(put("/danmaku/{id}/audit", 7L), 1001L, AUDIT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":2,\"auditRemark\":\"  \"}"))
                .andExpect(jsonPath("$.code").value(400));
        verify(danmakuMapper, never()).update(org.mockito.ArgumentMatchers.<Danmaku>any(), any());
    }

    @Test
    void approveClearsAiRejectReason() throws Exception {
        Danmaku aiRejected = stored(8L, 2);
        aiRejected.setAuditRemark("疑似违规");
        when(danmakuMapper.selectById(8L)).thenReturn(aiRejected);
        when(danmakuMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);

        mockMvc.perform(asUser(put("/danmaku/{id}/audit", 8L), 1001L, AUDIT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":1}"))
                .andExpect(jsonPath("$.code").value(200));

        // 修复前界面实测：通过后库中 audit_remark 仍是原拒绝原因
        LambdaUpdateWrapper<Danmaku> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("audit_remark=");
        assertThat(update.getParamNameValuePairs().values()).contains(1).containsNull();
    }

    @Test
    void onlyApproveOrRejectAreAccepted() throws Exception {
        when(danmakuMapper.selectById(7L)).thenReturn(stored(7L, 1));

        mockMvc.perform(asUser(put("/danmaku/{id}/audit", 7L), 1001L, AUDIT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":0}"))
                .andExpect(jsonPath("$.code").value(400));
        verify(danmakuMapper, never()).update(org.mockito.ArgumentMatchers.<Danmaku>any(), any());
    }

    @Test
    void unknownDanmakuIs404() throws Exception {
        mockMvc.perform(asUser(put("/danmaku/{id}/audit", 99L), 1001L, AUDIT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":1}"))
                .andExpect(jsonPath("$.code").value(404));
    }
}
