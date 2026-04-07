package com.zhixue.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.auth.form.LoginForm;
import com.zhixue.auth.service.LoginService;
import com.zhixue.auth.service.TokenService;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TokenControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private BeanFactory beanFactory;

    @Mock
    private TokenService tokenService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private LoginService passwordLoginService;

    @InjectMocks
    private TokenController tokenController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tokenController).build();
    }

    @Test
    void shouldLoginSuccessfullyWhenCaptchaValid() throws Exception {
        LoginForm form = new LoginForm();
        form.setLoginType("password");
        form.setUsername("admin");
        form.setPassword("123456");
        form.setUuid("captcha-uuid");
        form.setCode("ABCD");

        LoginUser loginUser = LoginUser.builder()
                .userId(1001L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .extra(Map.of("permissions", List.of("system:user:list")))
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("zhixue:auth:captcha:captcha-uuid")).thenReturn("ABCD");
        when(beanFactory.getBean(eq("passwordLoginService"), eq(LoginService.class))).thenReturn(passwordLoginService);
        when(passwordLoginService.login(any(LoginForm.class))).thenReturn(loginUser);
        when(tokenService.createToken(loginUser)).thenReturn("test-token");
        when(securityProperties.getJwtExpiration()).thenReturn(7200L);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(form)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("test-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.username").value("admin"));
    }

    @Test
    void shouldReturnUserInfoFromTokenCache() throws Exception {
        LoginUser loginUser = LoginUser.builder()
                .userId(1002L)
                .username("teacher")
                .roles(List.of("TEACHER"))
                .extra(Map.of(
                        "permissions", List.of("course:list", "course:publish"),
                        "nickname", "示例教师",
                        "avatar", "/avatar/teacher.png"))
                .build();

        when(tokenService.getLoginUser("teacher-token")).thenReturn(loginUser);

        mockMvc.perform(get("/user/info")
                        .header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("teacher"))
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.permissions[0]").value("course:list"))
                .andExpect(jsonPath("$.data.nickname").value("示例教师"));
    }
}
