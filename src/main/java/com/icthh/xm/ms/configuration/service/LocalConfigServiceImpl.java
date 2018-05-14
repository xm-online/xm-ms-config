package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.client.api.ConfigService;
import com.icthh.xm.commons.config.client.repository.ConfigurationModel;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
@Primary
@Component
public class LocalConfigServiceImpl implements ConfigService, ConfigurationModel {

    private final DistributedConfigRepository inMemoryRepository;
    private Consumer<Configuration> configurationListener;

    public Map<String, Configuration> getConfig() {
        return inMemoryRepository.getMap();
    }

    @Override
    public void onConfigurationChanged(Consumer<Configuration> configurationListener) {
        this.configurationListener = configurationListener;
    }

    @Override
    public void updateConfiguration(Collection<Configuration> configurations) {
        log.debug("Update yourself is not implemented yet");
    }
}
