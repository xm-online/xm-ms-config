package com.icthh.xm.ms.configuration.config.tenant;

import static com.icthh.xm.ms.configuration.config.Constants.*;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.config.Constants;
import io.undertow.util.LocaleUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Intercept JWT tenant and save it in context.
 */
@Slf4j
@Component
public class TenantInterceptor extends HandlerInterceptorAdapter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    private final ApplicationProperties applicationProperties;

    public TenantInterceptor(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        if (isIgnoredRequest(request)) {
            return true;
        }

        try {
            String tenant = setUserTenantContext(request);

            final boolean tenantSet;
            if (StringUtils.isBlank(tenant)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\": \"No tenant supplied\"}");
                response.getWriter().flush();
                tenantSet = false;
            } else {
                tenantSet = true;
            }

            return tenantSet;
        } catch (Exception e) {
            log.error("Error in tenant interceptor: ", e);
            throw e;
        }
    }

    private String setUserTenantContext(HttpServletRequest request) {
        String tenant;
        final OAuth2Authentication auth = getAuthentication();
        if (auth == null) {
            tenant = request.getHeader(Constants.HEADER_TENANT);
            TenantContext.setCurrent(tenant);
        } else {
            Map<String, String> details = null;

            if (auth.getDetails() != null) {
                details = Map.class.cast(OAuth2AuthenticationDetails.class.cast(auth.getDetails())
                                             .getDecodedDetails());
            }

            details = firstNonNull(details, new HashMap<>());

            tenant = details.getOrDefault(AUTH_TENANT_KEY, "");

            String xmToken = details.getOrDefault(AUTH_XM_TOKEN_KEY, "");
            String xmCookie = details.getOrDefault(AUTH_XM_COOKIE_KEY, "");
            String xmUserId = details.getOrDefault(AUTH_XM_USERID_KEY, "");
            String xmLocale = details.getOrDefault(AUTH_XM_LOCALE_KEY, "");
            String xmUserLogin = (String) auth.getPrincipal();

            TenantContext.setCurrent(new TenantInfo(tenant, xmToken, xmCookie, xmUserId, xmLocale, xmUserLogin));

            Locale locale = LocaleUtils.getLocaleFromString(xmLocale);
            if (locale != null) {
                LocaleContextHolder.setLocale(locale);
            }
        }
        return tenant;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
        throws Exception {

        // clear tenant context
        TenantContext.clear();
    }

    private static OAuth2Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2Authentication) {
            return (OAuth2Authentication) SecurityContextHolder.getContext().getAuthentication();
        }
        return null;
    }

    private boolean isIgnoredRequest(HttpServletRequest request) {
        String path = request.getServletPath();
        List<String> ignoredPatterns = applicationProperties.getTenantIgnoredPathList();
        if (ignoredPatterns != null && path != null) {
            for (String pattern : ignoredPatterns) {
                if (matcher.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

}
