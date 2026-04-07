package com.zhixue.auth.service.impl;

import com.zhixue.api.system.RemoteUserService;
import com.zhixue.auth.form.LoginForm;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatLoginServiceImplTest {

    @Mock
    private RemoteUserService remoteUserService;

    @Mock
    private SecurityProperties securityProperties;

    @InjectMocks
    private WechatLoginServiceImpl wechatLoginService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(wechatLoginService, "wechatMode", "sandbox");
        ReflectionTestUtils.setField(wechatLoginService, "sandboxUsername", "student");
    }

    @Test
    void shouldLoginWithSandboxMappedUser() {
        LoginForm form = new LoginForm();
        form.setWechatCode("wx-code-001");

        com.zhixue.api.system.domain.SysUser sysUser = new com.zhixue.api.system.domain.SysUser();
        sysUser.setId(1003L);
        sysUser.setUsername("student");
        sysUser.setNickname("示例学生");
        sysUser.setAvatar("student.png");
        sysUser.setStatus(0);

        com.zhixue.api.system.domain.LoginUser remoteUser = com.zhixue.api.system.domain.LoginUser.builder()
                .userId(1003L)
                .username("student")
                .roles(List.of("STUDENT"))
                .permissions(List.of("course:list"))
                .sysUser(sysUser)
                .build();

        when(securityProperties.getInternalToken()).thenReturn("internal-token");
        when(remoteUserService.getUserInfo("student", "true", "internal-token")).thenReturn(R.ok(remoteUser));

        LoginUser loginUser = wechatLoginService.login(form);
        assertThat(loginUser.getUserId()).isEqualTo(1003L);
        assertThat(loginUser.getRoles()).containsExactly("STUDENT");
        assertThat(loginUser.getExtra()).containsEntry("nickname", "示例学生");
    }

    @Test
    void shouldRejectWhenRealWechatModeNotEnabled() {
        ReflectionTestUtils.setField(wechatLoginService, "wechatMode", "real");

        LoginForm form = new LoginForm();
        form.setWechatCode("wx-code-002");

        assertThatThrownBy(() -> wechatLoginService.login(form))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前环境暂未启用真实微信登录");
    }
}

