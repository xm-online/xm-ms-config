package com.icthh.xm.ms.configuration.utils;

import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.EXTERNALS;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;
import static com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorageImpl.COMMONS_CONFIG;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import java.util.HashSet;

import com.icthh.xm.ms.configuration.service.dto.FullConfigurationDto;
import lombok.experimental.UtilityClass;
import org.springframework.util.AntPathMatcher;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@UtilityClass
public final class ConfigPathUtils {

    private static final int MAX_PATHS_TO_PRINT = 3;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private static final String TENANT_NAME = "tenantName";
    private static final String TENANT_PREFIX = CONFIG + TENANTS + "/";
    private static final String EXTERNAL_PATTERN = CONFIG + EXTERNALS + "/**";
    private static final String PATTERN = getTenantPathPrefix() + "{" + TENANT_NAME + "}/*/**";

    public static String getTenantPathPrefix(TenantContextHolder tenantContextHolder) {
        return getTenantPathPrefix() + getRequiredTenantKeyValue(tenantContextHolder);
    }

    public static List<Configuration> filterByTenant(List<Configuration> configurations, String tenant) {
        String tenantPathPrefix = getTenantPathPrefix(tenant) + "/";
        return configurations.stream()
            .filter(it -> it.getPath().startsWith(tenantPathPrefix))
            .collect(toList());
    }

    public static List<Configuration> filterByTenant(List<Configuration> configurations, Collection<String> tenants) {
        List<String> tenantPathPrefix = tenants.stream()
            .map(ConfigPathUtils::getTenantPathPrefix)
            .collect(toList());
        return configurations.stream()
            .filter(it -> tenantPathPrefix.stream().anyMatch(it.getPath()::startsWith))
            .collect(toList());
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

    public static boolean isExternalPath(String path) {
        return matcher.match(EXTERNAL_PATTERN, path);
    }

    public static FullConfigurationDto getFullConfiguration(List<Configuration> configurations) {
        FullConfigurationDto fullConfigurationDto = new FullConfigurationDto();
        for (Configuration configuration : configurations) {
            if (isExternalPath(configuration.getPath())) {
                fullConfigurationDto.computeExternalIfAbsent(configuration);
            } else {
                String tenantName = getTenantName(configuration.getPath()).orElse(COMMONS_CONFIG);
                fullConfigurationDto.computeTenantIfAbsent(tenantName, configuration);
                fullConfigurationDto.addChangedFiles(configuration.getPath());
            }
        }
        return fullConfigurationDto;
    }

    public static Map<String, Map<String, Configuration>> getTenants(List<Configuration> configurations) {
        return getFullConfiguration(configurations).getTenantsConfigs();
    }

    public static Map<String, Set<String>> getPathsByTenants(Collection<String> paths) {
        Map<String, Set<String>> byTenants = new HashMap<>();
        for (String path : paths) {
            getTenantName(path)
                .ifPresentOrElse(
                    tenant -> byTenants.computeIfAbsent(tenant, k -> new HashSet<>()).add(path),
                    () -> byTenants.computeIfAbsent(COMMONS_CONFIG, k -> new HashSet<>()).add(path)
                );
        }
        return byTenants;
    }

    public static Set<String> getTenantsByPaths(Collection<String> paths) {
        return paths.stream()
            .map(ConfigPathUtils::getTenantName)
            .map(it -> it.orElse(COMMONS_CONFIG))
            .collect(toSet());
    }

    public String printPathsWithLimit(Collection<String> paths) {
        if (paths != null) {
            List<String> result = paths.stream().limit(MAX_PATHS_TO_PRINT).collect(toList());
            if (paths.size() > MAX_PATHS_TO_PRINT) {
                result.add("...");
            }
            return result.toString();
        }
        return "";
    }

}
