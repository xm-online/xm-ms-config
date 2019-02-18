package com.icthh.xm.ms.configuration.utils;

import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;

import com.icthh.xm.commons.tenant.TenantContextHolder;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public final class ConfigPathUtils {

    private static final int MAX_PATHS_TO_PRINT = 3;

    public static String getTenantPathPrefix(TenantContextHolder tenantContextHolder) {
        return CONFIG + TENANTS + "/" + getRequiredTenantKeyValue(tenantContextHolder);
    }

    public static String getTenantPathPrefix(String tenant) {
        return CONFIG + TENANTS + "/" + tenant;
    }

    public String printPathsWithLimit(List<String> paths) {
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
