package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.service.PermissionConfigurationService;
import com.icthh.xm.ms.configuration.service.processors.ConfigurationProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A configuration processor that catches UAA-based permission property and loads
 * respective configuration.
 */
@Component
@Slf4j
class UaaPermissionsConfigurationProcessorImpl implements ConfigurationProcessor {

    private final ConfigProxyRepository configProxyRepository;
    private final PermissionConfigurationService permissionConfigurationService;

    public UaaPermissionsConfigurationProcessorImpl(@Lazy ConfigProxyRepository configProxyRepository,
                                                    @Lazy PermissionConfigurationService permissionConfigurationService) {
        this.configProxyRepository = configProxyRepository;
        this.permissionConfigurationService = permissionConfigurationService;
    }

    @Override
    public Configuration processConfiguration(Configuration configuration) {
        String path = configuration.getPath();

        if (path.endsWith(PermissionConfigurationService.TENANT_CONFIG_YML)
            && permissionConfigurationService.isUaaPermissionsEnabled(configuration)) {

            Map<String, Configuration> config = getConfiguration(extractTenantKey(path))
                .stream().collect(Collectors.toMap(Configuration::getPath, Function.identity()));
            configProxyRepository.getStorage().putAll(config);
        }

        return configuration;
    }

    private String extractTenantKey(String path) {
        String[] split = path.split("/");
        return split[split.length - 2]; //tenant key goes before the filename
    }

    private List<Configuration> getConfiguration(String tenantKey) {
        try {
            return permissionConfigurationService.getConfigs(tenantKey);
        } catch (IllegalStateException e) {
            log.info("Failed to load configuration from UAA, might not started yet: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to load configuration from UAA", e);
        }
        return Collections.emptyList();
    }
}
