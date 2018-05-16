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
public class LocalConfigService implements ConfigService {

    private final DistributedConfigRepository inMemoryRepository;
    private Consumer<Configuration> configurationListener;

    @Override
    public Map<String, Configuration> getConfigurationMap(String commit) {
        return inMemoryRepository.getMap(commit);
    }

    @Override
    public void onConfigurationChanged(Consumer<Configuration> configurationListener) {
        this.configurationListener = configurationListener;
    }

    @Override
    public void updateConfigurations(String commit, Collection<String> paths) {
        Map<String, Configuration> configurationsMap = inMemoryRepository.getMap(commit);
        paths.forEach(path -> notifyUpdated(configurationsMap
            .getOrDefault(path, new Configuration(path, null, null))));
    }

    private void notifyUpdated(Configuration configuration) {
        log.debug("Notify configuration changed [{}]", configuration.getPath());
        Optional.ofNullable(configurationListener)
            .ifPresent(configurationListener -> configurationListener.accept(configuration));
    }
}
