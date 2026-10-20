package com.zhixue.ai.chat;

import com.zhixue.common.security.utils.SecurityUtils;

/**
 * 提问者身份。
 *
 * <p><b>必须在请求线程上取</b>：身份来自网关注入的请求头，进入流式处理后读不到。
 * 落库归属与「是否已购」判定都靠这里取出的值。</p>
 */
public record Viewer(Long userId, boolean admin) {

    /** 当前登录用户；未登录抛 401 */
    public static Viewer current() {
        return new Viewer(SecurityUtils.requireUserId(), SecurityUtils.hasAnyRole("ADMIN"));
    }
}
