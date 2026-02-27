package com.zhixue.auth.service;

import com.zhixue.common.security.model.LoginUser;

/**
 * 登录凭证服务接口
 * 作用：这个接口定义了登录凭证的生成、刷新、删除和查询方法。
 * 登录凭证就像一张门票，用户登录成功后得到这张门票，后续访问其他服务时需要出示门票。
 */
public interface TokenService {

    /**
     * 根据登录用户信息生成登录凭证，并缓存到Redis中。
     */
    String createToken(LoginUser loginUser);

    /**
     * 刷新登录凭证的有效期。
     */
    String refreshToken(String token);

    /**
     * 删除登录凭证，相当于退出登录。
     */
    void logout(String token);

    /**
     * 根据登录凭证查询登录用户信息。
     */
    LoginUser getLoginUser(String token);
}

