package com.icthh.xm.ms.configuration.security;

import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Component
@RequiredArgsConstructor
public class AdminApiAccessInterceptor extends HandlerInterceptorAdapter {

    private final ApplicationProperties applicationProperties;
    private final TenantContextHolder tenantContextHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();

        if (Boolean.TRUE.equals(applicationProperties.getAdminApiRestrictionEnabled())) {
            if (requestURI.startsWith("/api/config") || requestURI.startsWith("/api/tenants")) {
                String tenantKey = tenantContextHolder.getTenantKey().toUpperCase();
                    List<String> superTenantsList = applicationProperties.getSuperTenantsList();
                    if (firstNonNull(superTenantsList, emptyList()).contains(tenantKey)) {
                        return true;
                    }
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only privileges tenant can access admin api");
                return false;
            }
        }

        return true;
    }
}
