package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.icthh.xm.ms.configuration.repository.impl.MultiGitRepository.EXTERNAL_TENANTS_CONFIG;

@Component
@RequiredArgsConstructor
public class ExternalTenantsRefreshableConfiguration implements RefreshableConfiguration {

    private final ConfigurationService configurationService;

    @Override
    public void onRefresh(String updatedKey, String config) {
        configurationService.refreshConfiguration(List.of(EXTERNAL_TENANTS_CONFIG));
    }

    @Override
    public boolean isListeningConfiguration(String updatedKey) {
        return EXTERNAL_TENANTS_CONFIG.equals(updatedKey);
    }

    @Override
    public void onInit(String configKey, String configValue) {
        onRefresh(configKey, configValue);
    }
}
