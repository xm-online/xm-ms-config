package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.ms.configuration.domain.Configuration;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InMemoryRepository implements DistributedConfigRepository {

    private final ConcurrentMap<String, String> storage = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> getMap() {
        return storage;
    }

    @Override
    public Configuration find(String path) {
        log.debug("Get configuration from memory by path {}", path);
        if (getMap().containsKey(path)) {
            return new Configuration(path, getMap().get(path));
        } else {
            return null;
        }
    }

    @Override
    public void save(Configuration configuration) {
        log.info("Save configuration to memory with path {}", configuration.getPath());
        getMap().put(configuration.getPath(), configuration.getContent());
    }

    @Override
    public void saveAll(List<Configuration> configurations) {
        log.info("Save all configuration to memory with path {}", configurations.stream().map
            (Configuration::getPath).collect(Collectors.toList()));
        Map<String, String> map = new HashMap<>();
        configurations.forEach(c -> map.put(c.getPath(), c.getContent()));
        getMap().putAll(map);
    }

    @Override
    public void delete(String path) {
        log.info("Delete configuration from memory by path {}", path);
        getMap().remove(path);
    }

    @Override
    public List<String> getKeysList() {
        return new ArrayList<>(getMap().keySet());
    }

}
