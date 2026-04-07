package com.zhixue.auth.service.impl;

import com.zhixue.api.system.RemoteUserService;
import com.zhixue.auth.form.LoginForm;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordLoginServiceImplTest {

    @Mock
    private RemoteUserService remoteUserService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SecurityProperties securityProperties;

    @InjectMocks
    private PasswordLoginServiceImpl passwordLoginService;

    @Test
    void shouldLoginWithRealUserPayload() {
        LoginForm form = new LoginForm();
        form.setUsername("admin");
        form.setPassword("Admin@123");

        com.zhixue.api.system.domain.SysUser sysUser = new com.zhixue.api.system.domain.SysUser();
        sysUser.setId(1L);
        sysUser.setUsername("admin");
        sysUser.setPassword("$2a$10$encoded");
        sysUser.setNickname("管理员");
        sysUser.setAvatar("avatar.png");
        sysUser.setStatus(0);

        com.zhixue.api.system.domain.LoginUser remoteLoginUser = com.zhixue.api.system.domain.LoginUser.builder()
                .userId(1L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .permissions(List.of("system:user:list", "course:list"))
                .sysUser(sysUser)
                .build();

        when(securityProperties.getInternalToken()).thenReturn("internal-token");
        when(remoteUserService.getUserInfo("admin", "true", "internal-token")).thenReturn(R.ok(remoteLoginUser));
        when(passwordEncoder.matches("Admin@123", "$2a$10$encoded")).thenReturn(true);

        LoginUser loginUser = passwordLoginService.login(form);

        assertThat(loginUser.getUserId()).isEqualTo(1L);
        assertThat(loginUser.getRoles()).containsExactly("ADMIN");
        assertThat(loginUser.getExtra()).containsEntry("nickname", "管理员");
        assertThat(loginUser.getExtra()).containsEntry("avatar", "avatar.png");
        assertThat((List<String>) loginUser.getExtra().get("permissions")).contains("system:user:list", "course:list");
    }

    @Test
    void shouldRejectDisabledUser() {
        LoginForm form = new LoginForm();
        form.setUsername("teacher01");
        form.setPassword("Teacher@123");

        com.zhixue.api.system.domain.SysUser sysUser = new com.zhixue.api.system.domain.SysUser();
        sysUser.setStatus(1);

        com.zhixue.api.system.domain.LoginUser remoteLoginUser = com.zhixue.api.system.domain.LoginUser.builder()
                .userId(2L)
                .username("teacher01")
                .roles(List.of("TEACHER"))
                .sysUser(sysUser)
                .build();

        when(securityProperties.getInternalToken()).thenReturn("internal-token");
        when(remoteUserService.getUserInfo("teacher01", "true", "internal-token")).thenReturn(R.ok(remoteLoginUser));

        assertThatThrownBy(() -> passwordLoginService.login(form))
                .isInstanceOf(ServiceException.class)
                .hasMessage("账号已停用");
    }
}
