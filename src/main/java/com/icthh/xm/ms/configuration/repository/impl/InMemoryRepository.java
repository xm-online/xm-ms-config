package com.icthh.xm.ms.configuration.repository.impl;

import static java.util.Collections.singletonList;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.config.domain.ConfigurationEvent;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class InMemoryRepository implements DistributedConfigRepository {

    public static final String LOG_CONFIG_EMPTY = "<CONFIG_EMPTY>";

    private final ConcurrentMap<String, Configuration> storage = new ConcurrentHashMap<>();
    private final ConfigTopicProducer configTopicProducer;

    @Override
    public Map<String, Configuration> getMap() {
        return storage;
    }

    @Override
    public Configuration find(String path) {
        log.debug("Get configuration from memory by path {}", path);
        return getMap().get(path);
    }

    @Override
    public void save(Configuration configuration) {
        log.info("Save configuration to memory with path {}", configuration.getPath());
        getMap().put(configuration.getPath(), configuration);
        configTopicProducer.notifyConfigurationChanged(singletonList(
            new ConfigurationEvent(configuration.getPath(), configuration.getCommit())));
    }

    @Override
    public void saveAll(List<Configuration> configurations) {
        log.info("Save all configuration to memory with path {}", configurations.stream().map
            (Configuration::getPath).collect(Collectors.toList()));
        Map<String, Configuration> map = new HashMap<>();
        configurations.forEach(configuration -> map.put(configuration.getPath(), configuration));
        getMap().putAll(map);
        configTopicProducer.notifyConfigurationChanged(configurations.stream()
            .map(configuration -> new ConfigurationEvent(configuration.getPath(), configuration.getCommit()))
            .collect(Collectors.toList()));
    }

    @Override
    public void delete(String path) {
        log.info("Delete configuration from memory by path {}", path);
        getMap().remove(path);
        configTopicProducer.notifyConfigurationChanged(singletonList(new ConfigurationEvent(path, null)));
    }

    @Override
    public List<String> getKeysList() {
        return new ArrayList<>(getMap().keySet());
    }

    private String getValueHash(final String configContent) {
        return StringUtils.isEmpty(configContent) ? LOG_CONFIG_EMPTY :
            DigestUtils.md5Hex(configContent);
    }

}
