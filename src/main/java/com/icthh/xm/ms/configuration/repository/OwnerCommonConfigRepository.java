package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.client.repository.CommonConfigRepository;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OwnerCommonConfigRepository implements CommonConfigRepository {

    private final ConfigurationService configurationService;

    @Override
    public Map<String, Configuration> getConfig(String version) {
        return configurationService.getConfigurationMap(version);
    }

    @SneakyThrows
    @Override
    public Map<String, Configuration> getConfig(String version, Collection<String> paths) {
        return configurationService.getConfigurationMap(version, paths);
    }

    @Override
    public void updateConfigFullPath(Configuration configuration, String oldConfigHash) {
        configurationService.updateConfiguration(configuration, oldConfigHash);
    }
}
