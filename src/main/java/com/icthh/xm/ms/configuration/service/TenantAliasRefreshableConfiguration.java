package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.service.TenantAliasTreeService.TENANT_ALIAS_CONFIG;

import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.commons.config.domain.Configuration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAliasRefreshableConfiguration implements RefreshableConfiguration {

    private final TenantAliasTreeService tenantAliasService;

    @Override
    public void onRefresh(String updatedKey, String config) {
        tenantAliasService.updateAliasTree(new Configuration(updatedKey, config));
    }

    @Override
    public boolean isListeningConfiguration(String updatedKey) {
        return TENANT_ALIAS_CONFIG.equals(updatedKey);
    }

    @Override
    public void onInit(String configKey, String configValue) {
        onRefresh(configKey, configValue);
    }

}
