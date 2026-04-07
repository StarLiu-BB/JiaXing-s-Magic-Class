package com.zhixue.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.utils.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 校验网关和服务间透传的内部请求头，避免旁路请求伪造身份。
 */
public class InternalAccessFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalAccessFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean carriesInternalContext = hasInternalContext(request);
        boolean trusted = isTrusted(request);

        if (carriesInternalContext && !trusted) {
            writeUnauthorized(response, "非法内部请求");
            return;
        }

        if (trusted) {
            request.setAttribute(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE);
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasInternalContext(HttpServletRequest request) {
        return StringUtils.isNotBlank(request.getHeader(InternalAccessConstants.USER_ID_HEADER))
                || StringUtils.isNotBlank(request.getHeader(InternalAccessConstants.USER_NAME_HEADER))
                || StringUtils.isNotBlank(request.getHeader(InternalAccessConstants.USER_ROLES_HEADER))
                || StringUtils.isNotBlank(request.getHeader(InternalAccessConstants.USER_PERMISSIONS_HEADER))
                || StringUtils.isNotBlank(request.getHeader(InternalAccessConstants.INNER_CALL_HEADER))
                || StringUtils.isNotBlank(request.getHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER));
    }

    private boolean isTrusted(HttpServletRequest request) {
        String configuredToken = securityProperties.getInternalToken();
        String headerToken = request.getHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER);
        return StringUtils.isNotBlank(configuredToken) && configuredToken.equals(headerToken);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(HttpStatus.UNAUTHORIZED, message)));
    }
}
