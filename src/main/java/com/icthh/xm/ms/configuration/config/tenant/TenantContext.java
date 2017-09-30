package com.icthh.xm.ms.configuration.config.tenant;

import com.icthh.xm.commons.logging.util.MDCUtil;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TenantContext {

    public static final String DEFAULT_TENANT = "XM";

    private static ThreadLocal<TenantInfo> current = ThreadLocal
        .withInitial(() -> new TenantInfo(DEFAULT_TENANT, "", "", "", "", ""));

    public static void setCurrent(TenantInfo userInfo) {
        current.set(userInfo);
        MDCUtil.putRid(MDCUtil.generateRid() + ":" + userInfo.getUserLogin() + ":" + userInfo.getTenant());
    }

    public static void setCurrent(String tenant) {
        setCurrent(new TenantInfo(tenant, "", "", "", "", ""));
    }

    public static TenantInfo getCurrent() {
        return current.get();
    }

    public static void clear() {
        current.remove();
        MDCUtil.clear();
    }

}
