package com.icthh.xm.ms.configuration.utils;

import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;

import com.icthh.xm.commons.tenant.TenantContextHolder;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ConfigPathUtils {

    public static String getTenantPathPrefix(TenantContextHolder tenantContextHolder) {
        return CONFIG + TENANTS + "/" + getRequiredTenantKeyValue(tenantContextHolder);
    }

    public static String getTenantPathPrefix(String tenant) {
        return CONFIG + TENANTS + "/" + tenant;
    }

}
