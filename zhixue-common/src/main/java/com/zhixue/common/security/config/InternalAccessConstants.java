package com.zhixue.common.security.config;

/**
 * 内部请求头常量。
 */
public final class InternalAccessConstants {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_NAME_HEADER = "X-User-Name";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String USER_PERMISSIONS_HEADER = "X-User-Permissions";
    public static final String INNER_CALL_HEADER = "X-Inner-Call";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String TRUSTED_REQUEST_ATTR = "zhixue.trustedInternalRequest";

    private InternalAccessConstants() {
    }
}
