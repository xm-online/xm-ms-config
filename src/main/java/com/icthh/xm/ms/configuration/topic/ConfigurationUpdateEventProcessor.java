package com.icthh.xm.ms.configuration.topic;

import com.icthh.xm.commons.config.client.repository.message.ConfigurationUpdateMessage;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigurationUpdateEventProcessor {

    private final TenantContextHolder tenantContextHolder;
    private final LepManagementService lepManagementService;
    private final ConfigurationService configurationService;

    public void process(ConfigurationUpdateMessage message, String tenant) {
        try {
            init(tenant);
            Configuration configuration = new Configuration(message.getPath(), message.getContent());
            configurationService.updateConfiguration(configuration, message.getOldConfigHash());

        } catch (ConcurrentConfigModificationException e) {
            log.warn("Error occurred when update configuration", e);
            throw e;
        } finally {
            destroy();
        }
    }

    private void init(String tenantKey) {
        if (StringUtils.isNotBlank(tenantKey)) {
            TenantContextUtils.setTenant(tenantContextHolder, tenantKey);
            lepManagementService.beginThreadContext();
        }
        MdcUtils.putRid(MdcUtils.getRid() + "::" + StringUtils.defaultIfBlank(tenantKey, ""));
    }

    private void destroy() {
        lepManagementService.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
        MdcUtils.removeRid();
    }
}
