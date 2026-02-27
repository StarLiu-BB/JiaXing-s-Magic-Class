package com.zhixue.auth.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录表单类
 * 作用：这个类用来接收前端传来的登录信息。
 * 支持多种登录方式：密码登录、短信验证码登录、微信登录。
 * 根据不同的登录类型，需要填写不同的字段。
 */
@Data
public class LoginForm {

    /**
     * 登录类型：password（密码登录） / sms（短信登录） / wechat（微信登录）
     */
    @NotBlank(message = "登录类型不能为空")
    private String loginType;

    private String username;

    private String password;

    private String phone;

    private String smsCode;

    private String wechatCode;
}

