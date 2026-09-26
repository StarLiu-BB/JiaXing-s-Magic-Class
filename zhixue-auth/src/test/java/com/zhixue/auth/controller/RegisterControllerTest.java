package com.zhixue.auth.controller;

import com.zhixue.auth.form.RegisterForm;
import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注册接口测试。
 *
 * <p>覆盖两个缺陷：</p>
 * <ul>
 *   <li>D16-路由：网关 /auth/** 带 StripPrefix=1，下游收到的是 /register，
 *       因此控制器不得再带 /auth 类级前缀，否则该白名单接口 404。</li>
 *   <li>D16-假成功：原实现返回 R.ok() 却不创建任何用户。该接口在网关白名单中
 *       公开可调，返回成功会让调用方误以为注册完成。未实现就必须显式失败。</li>
 * </ul>
 */
class RegisterControllerTest {

    @Test
    void shouldNotDeclareAuthPrefixBecauseGatewayStripsIt() {
        RequestMapping mapping = RegisterController.class.getAnnotation(RequestMapping.class);

        if (mapping != null) {
            assertThat(mapping.value())
                    .as("网关已 StripPrefix=1，控制器不得再带 /auth 前缀，否则 /auth/register 404")
                    .doesNotContain("/auth");
        }
    }

    @Test
    void shouldMatchTokenControllerMappingConvention() {
        // TokenController / CaptchaController 均无类级前缀，注册接口必须保持一致
        assertThat(TokenController.class.getAnnotation(RequestMapping.class))
                .as("TokenController 不应有类级映射（作为约定基准）")
                .isNull();
        assertThat(RegisterController.class.getAnnotation(RequestMapping.class))
                .as("RegisterController 必须与同模块其他控制器保持一致，不带类级前缀")
                .isNull();
    }

    @Test
    void shouldRejectInsteadOfReturningFakeSuccess() {
        RegisterController controller = new RegisterController();
        RegisterForm form = new RegisterForm();
        form.setUsername("newuser");
        form.setPassword("Passw0rd!");
        form.setPhone("13800000001");
        form.setSmsCode("123456");

        // 未接入真实建号流程前，绝不能返回成功，否则是对调用方撒谎
        assertThatThrownBy(() -> controller.register(form))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂未开放");
    }

    @Test
    void shouldStillValidateBlankUsername() {
        RegisterController controller = new RegisterController();
        RegisterForm form = new RegisterForm();
        form.setUsername("  ");

        assertThatThrownBy(() -> controller.register(form))
                .isInstanceOf(ServiceException.class);
    }
}
