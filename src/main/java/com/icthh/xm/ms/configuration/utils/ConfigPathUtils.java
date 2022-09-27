package com.icthh.xm.ms.configuration.utils;

import static com.icthh.xm.commons.config.client.service.TenantConfigService.DEFAULT_TENANT_CONFIG_PATTERN;
import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;

import com.icthh.xm.commons.tenant.TenantContextHolder;
import lombok.experimental.UtilityClass;
import org.springframework.util.AntPathMatcher;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@UtilityClass
public final class ConfigPathUtils {

    private static final int MAX_PATHS_TO_PRINT = 3;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private static final String TENANT_NAME = "tenantName";
    private static final String TENANT_PREFIX = CONFIG + TENANTS + "/";
    private static final String PATTERN = getTenantPathPrefix() + "{" + TENANT_NAME + "}/**";

    public static String getTenantPathPrefix(TenantContextHolder tenantContextHolder) {
        return getTenantPathPrefix() + getRequiredTenantKeyValue(tenantContextHolder);
    }

    public static String getTenantPathPrefix(String tenant) {
        return getTenantPathPrefix() + tenant;
    }

    public static String getTenantPathPrefix() {
        return TENANT_PREFIX;
    }

    public static Optional<String> getTenantName(String path) {
        if (isUnderTenantFolder(path)) {
            return Optional.ofNullable(matcher.extractUriTemplateVariables(PATTERN, path).get(TENANT_NAME));
        }
        return Optional.empty();
    }

    public static String getPathInTenant(String path, String targetTenant) {
        return getTenantName(path)
                .map(sourceTenant -> replaceTenantName(path, sourceTenant, targetTenant))
                .orElse(path);
    }

    private static String replaceTenantName(String path, String sourceTenant, String targetTenant) {
        return getTenantPathPrefix(targetTenant) + path.substring(getTenantPathPrefix(sourceTenant).length());
    }

    public static boolean isUnderTenantFolder(String path) {
        return matcher.match(PATTERN, path);
    }

    public String printPathsWithLimit(Collection<String> paths) {
        if (paths != null) {
            List<String> result = paths.stream().limit(MAX_PATHS_TO_PRINT).collect(Collectors.toList());
            if (paths.size() > MAX_PATHS_TO_PRINT) {
                result.add("...");
            }
            return result.toString();
        }
        return "";
    }

}
