package com.zhixue.auth.controller;

import com.zhixue.auth.form.RegisterForm;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册控制器。
 *
 * <p>路由说明：网关 {@code /auth/**} 已配置 StripPrefix=1，下游收到的路径是
 * {@code /register}，因此此处不能再声明 {@code /auth} 类级前缀
 * （与 TokenController、CaptchaController 保持一致）。</p>
 *
 * <p>实现状态：真实建号流程（短信校验 + 唯一性检查 + 默认角色分配）需要
 * system 服务提供内部注册端点，随 C 端注册页一并交付。在此之前本接口
 * 显式拒绝，不返回假成功——该接口在网关白名单中公开可调，
 * 返回成功会让调用方误判注册已完成。</p>
 */
@Slf4j
@RestController
public class RegisterController {

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterForm form) {
        if (StringUtils.isBlank(form.getUsername())) {
            throw new ServiceException("用户名不能为空");
        }
        log.warn("收到注册请求但功能尚未开放 username={}", form.getUsername());
        throw new ServiceException("账号注册功能暂未开放，请联系管理员开通账号");
    }
}
