package com.zhixue.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册表单类
 * 作用：这个类用来接收前端传来的注册信息。
 * 新用户注册时需要填写用户名、密码、手机号和短信验证码。
 */
@Data
public class RegisterForm {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    private String smsCode;
}

