package com.zhixue.common.security.config;

import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.annotation.RequireLogin;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.common.security.annotation.RequireRole;
import com.zhixue.common.security.utils.SecurityUtils;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

/**
 * 方法级鉴权切面。
 */
@Aspect
public class SecurityAccessAspect {

    @Before("@annotation(requireLogin)")
    public void checkLogin(RequireLogin requireLogin) {
        if (!SecurityUtils.isLoggedIn()) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "未登录或令牌已失效");
        }
    }

    @Before("@annotation(requireRole)")
    public void checkRole(RequireRole requireRole) {
        checkLogin(null);
        boolean allowed = requireRole.allMatch()
                ? SecurityUtils.hasAllRoles(requireRole.value())
                : SecurityUtils.hasAnyRole(requireRole.value());
        if (!allowed) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "无角色权限访问该接口");
        }
    }

    @Before("@annotation(requirePermission)")
    public void checkPermission(RequirePermission requirePermission) {
        checkLogin(null);
        boolean allowed = requirePermission.allMatch()
                ? SecurityUtils.hasAllPermissions(requirePermission.value())
                : SecurityUtils.hasAnyPermission(requirePermission.value());
        if (!allowed) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "无接口权限访问该资源");
        }
    }
}
