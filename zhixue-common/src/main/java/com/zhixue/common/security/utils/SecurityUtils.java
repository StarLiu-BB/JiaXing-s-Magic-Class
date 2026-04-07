package com.zhixue.common.security.utils;

import com.zhixue.common.core.utils.ServletUtils;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.common.security.config.InternalAccessConstants;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 基于网关透传请求头的鉴权工具。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static boolean isLoggedIn() {
        return isTrustedUserContext() && StringUtils.isNotBlank(ServletUtils.getHeader(InternalAccessConstants.USER_ID_HEADER));
    }

    public static List<String> getRoles() {
        return isTrustedUserContext()
                ? parseHeaderList(ServletUtils.getHeader(InternalAccessConstants.USER_ROLES_HEADER))
                : Collections.emptyList();
    }

    public static List<String> getPermissions() {
        return isTrustedUserContext()
                ? parseHeaderList(ServletUtils.getHeader(InternalAccessConstants.USER_PERMISSIONS_HEADER))
                : Collections.emptyList();
    }

    public static Long getUserId() {
        if (!isTrustedUserContext()) {
            return null;
        }
        String userId = ServletUtils.getHeader(InternalAccessConstants.USER_ID_HEADER);
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean isTrustedInternalCall() {
        return isTrustedRequest()
                && "true".equalsIgnoreCase(ServletUtils.getHeader(InternalAccessConstants.INNER_CALL_HEADER));
    }

    public static boolean hasAnyRole(String... roles) {
        List<String> currentRoles = getRoles();
        return Arrays.stream(roles)
                .filter(StringUtils::isNotBlank)
                .anyMatch(required -> currentRoles.stream().anyMatch(required::equalsIgnoreCase));
    }

    public static boolean hasAllRoles(String... roles) {
        List<String> currentRoles = getRoles();
        return Arrays.stream(roles)
                .filter(StringUtils::isNotBlank)
                .allMatch(required -> currentRoles.stream().anyMatch(required::equalsIgnoreCase));
    }

    public static boolean hasAnyPermission(String... permissions) {
        List<String> currentPermissions = getPermissions();
        return Arrays.stream(permissions)
                .filter(StringUtils::isNotBlank)
                .anyMatch(currentPermissions::contains);
    }

    public static boolean hasAllPermissions(String... permissions) {
        List<String> currentPermissions = getPermissions();
        return Arrays.stream(permissions)
                .filter(StringUtils::isNotBlank)
                .allMatch(currentPermissions::contains);
    }

    private static List<String> parseHeaderList(String rawHeader) {
        if (StringUtils.isBlank(rawHeader)) {
            return Collections.emptyList();
        }
        String normalized = rawHeader.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (StringUtils.isBlank(normalized)) {
            return Collections.emptyList();
        }
        return Arrays.stream(normalized.split(","))
                .map(SecurityUtils::normalizeItem)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private static String normalizeItem(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static boolean isTrustedUserContext() {
        return isTrustedRequest() && StringUtils.isNotBlank(ServletUtils.getHeader(InternalAccessConstants.USER_ID_HEADER));
    }

    private static boolean isTrustedRequest() {
        HttpServletRequest request = ServletUtils.getRequest();
        return request != null && Boolean.TRUE.equals(request.getAttribute(InternalAccessConstants.TRUSTED_REQUEST_ATTR));
    }
}
