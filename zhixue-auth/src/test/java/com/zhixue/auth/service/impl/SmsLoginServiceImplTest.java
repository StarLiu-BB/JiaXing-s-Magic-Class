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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsLoginServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RemoteUserService remoteUserService;

    @Mock
    private SecurityProperties securityProperties;

    @InjectMocks
    private SmsLoginServiceImpl smsLoginService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(smsLoginService, "smsMode", "sandbox");
        ReflectionTestUtils.setField(smsLoginService, "sandboxSmsCode", "123456");
    }

    @Test
    void shouldLoginSuccessfullyInSandboxMode() {
        LoginForm form = new LoginForm();
        form.setPhone("13800000003");
        form.setSmsCode("123456");

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

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(securityProperties.getInternalToken()).thenReturn("internal-token");
        when(remoteUserService.getUserInfoByPhone("13800000003", "true", "internal-token")).thenReturn(R.ok(remoteUser));

        LoginUser loginUser = smsLoginService.login(form);

        assertThat(loginUser.getUsername()).isEqualTo("student");
        assertThat(loginUser.getRoles()).containsExactly("STUDENT");
        assertThat((List<String>) loginUser.getExtra().get("permissions")).containsExactly("course:list");
    }

    @Test
    void shouldRejectWhenSmsCodeInvalid() {
        LoginForm form = new LoginForm();
        form.setPhone("13800000003");
        form.setSmsCode("000000");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> smsLoginService.login(form))
                .isInstanceOf(ServiceException.class)
                .hasMessage("短信验证码错误或已过期");
    }
}

