package com.zhixue.auth.service;

import com.zhixue.auth.form.LoginForm;
import com.zhixue.common.security.model.LoginUser;

/**
 * 登录服务接口
 * 作用：这个接口定义了登录的标准方法。
 * 不同的登录方式（密码、短信、微信）都实现这个接口，但具体的验证逻辑不同。
 * 这样设计的好处是，新增登录方式时只需要添加一个新的实现类，不需要修改现有代码。
 */
public interface LoginService {

    /**
     * 执行登录验证并返回登录用户信息。
     *
     * @param form 登录表单，包含用户名、密码或验证码等信息
     * @return 登录用户信息，包含用户ID、用户名、角色等
     */
    LoginUser login(LoginForm form);
}

