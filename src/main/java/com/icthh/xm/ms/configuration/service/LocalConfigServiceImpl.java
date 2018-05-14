package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.client.api.ConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
@Primary
@Component
public class LocalConfigServiceImpl implements ConfigService {

    private final DistributedConfigRepository inMemoryRepository;
    private Consumer<Configuration> configurationListener;

    @Override
    public Map<String, Configuration> getConfigurationMap() {
        return inMemoryRepository.getMap();
    }

    @Override
    public void onConfigurationChanged(Consumer<Configuration> configurationListener) {
        this.configurationListener = configurationListener;
    }

    @Override
    public void updateConfigurations(Collection<Configuration> configurations) {
        Map<String, Configuration> configurationsMap = inMemoryRepository.getMap();
        configurations.forEach(configuration -> notifyUpdated(configurationsMap
            .getOrDefault(configuration.getPath(), new Configuration(configuration.getPath(), null, null))));
    }

    private void notifyUpdated(Configuration configuration) {
        log.debug("Notify configuration changed [{}]", configuration.getPath());
        Optional.ofNullable(configurationListener)
            .ifPresent(configurationListener -> configurationListener.accept(configuration));
    }
}
