package com.zhixue.auth.controller;

import com.zhixue.auth.form.RegisterForm;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.utils.StringUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册控制器
 * 作用：这个类提供用户注册的接口。
 * 新用户可以通过这个接口注册账号，填写用户名、密码、手机号和验证码。
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class RegisterController {

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterForm form) {
        // TODO: 调用系统服务创建用户并校验验证码
        if (StringUtils.isBlank(form.getUsername())) {
            throw new ServiceException("用户名不能为空");
        }
        log.info("注册用户 username={}, phone={}", form.getUsername(), form.getPhone());
        return R.ok();
    }
}

