package com.zhixue.common.core.utils;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 请求响应工具类
 * 作用：这个类提供获取当前请求和响应对象的方法。
 * 还可以把结果直接写给前端，比如返回JSON格式的错误信息。
 * 方便在任何地方都能拿到当前请求的信息，或者直接返回结果。
 */
public final class ServletUtils {

    // 不让别人创建这个类的对象，因为只用来提供工具方法
    private ServletUtils() {
    }

    // 获取当前的请求对象
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    // 获取当前的响应对象
    public static HttpServletResponse getResponse() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getResponse() : null;
    }

    // 把字符串内容写给前端，比如返回JSON格式的错误信息
    public static void renderString(HttpServletResponse response, String content) {
        if (response == null) {
            return;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write(content);
        } catch (IOException e) {
            throw new IllegalStateException("写出响应失败", e);
        }
    }

    // 获取请求头里指定名字的值
    public static String getHeader(String name) {
        HttpServletRequest request = getRequest();
        return request != null ? request.getHeader(name) : null;
    }

    // 获取客户端的IP地址，考虑了代理转发的情况
    public static String getClientIp(ServletRequest servletRequest) {
        if (servletRequest == null) {
            return null;
        }
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(ip)) {
            return ip.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    // 关闭响应流，释放资源
    public static void closeResponse(ServletResponse response) {
        if (response == null) {
            return;
        }
        try {
            response.getOutputStream().close();
        } catch (IOException ignored) {
        }
    }
}

