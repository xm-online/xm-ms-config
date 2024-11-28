package com.icthh.xm.ms.configuration.topic;

import com.icthh.xm.commons.config.client.repository.message.ConfigurationUpdateMessage;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.lep.api.LepEngineSession;
import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.logging.util.MdcUtils;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import static com.icthh.xm.commons.tenant.TenantContextUtils.buildTenant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigurationUpdateEventProcessor {

    private final TenantContextHolder tenantContextHolder;
    private final LepManagementService lepManagementService;
    private final ConfigurationService configurationService;

    public void process(ConfigurationUpdateMessage message, String tenant) {
        MdcUtils.putRid(MdcUtils.getRid() + "::" + StringUtils.defaultIfBlank(tenant, ""));

        tenantContextHolder.getPrivilegedContext().execute(buildTenant(tenant.toUpperCase()), () -> {
            try (LepEngineSession context = lepManagementService.beginThreadContext()) {

                Configuration configuration = new Configuration(message.getPath(), message.getContent());
                configurationService.updateConfiguration(configuration, message.getOldConfigHash());

            } catch (ConcurrentConfigModificationException e) {
                log.warn("Error occurred when update configuration", e);
                throw e;
            }
            MdcUtils.removeRid();
        });
    }
}
