package com.icthh.xm.ms.configuration.repository;

import static com.icthh.xm.ms.configuration.config.HazelcastConfiguration.TENANT_CONFIGURATION_HAZELCAST;
import static com.icthh.xm.ms.configuration.config.HazelcastConfiguration.TENANT_CONFIGURATION_MAP;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.icthh.xm.ms.configuration.domain.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HazelcastRepository implements DistributedConfigRepository {

    private final HazelcastInstance hazelcast;

    public HazelcastRepository(@Qualifier(TENANT_CONFIGURATION_HAZELCAST) HazelcastInstance hazelcast) {
        this.hazelcast = hazelcast;
    }

    @Override
    public Configuration find(String path) {
        log.debug("Get configuration from hazelcast by path {}", path);
        if (getMap().containsKey(path)) {
            return new Configuration(path, getMap().get(path));
        } else {
            return null;
        }
    }

    private IMap<String, String> getMap() {
        return hazelcast.getMap(TENANT_CONFIGURATION_MAP);
    }

    @Override
    public void save(Configuration configuration) {
        log.info("Save configuration to hazelcast with path {}", configuration.getPath());
        getMap().put(configuration.getPath(), configuration.getContent());
    }

    @Override
    public void saveAll(List<Configuration> configurations) {
        log.info("Save all configuration to hazelcast with path {}", configurations.stream().map
            (Configuration::getPath).collect(Collectors.toList()));
        Map<String, String> map = new HashMap<>();
        configurations.forEach(c -> map.put(c.getPath(), c.getContent()));
        getMap().putAll(map);
    }

    @Override
    public void delete(String path) {
        log.info("Delete configuration from hazelcast by path {}", path);
        getMap().remove(path);
    }

    @Override
    public List<String> getKeysList() {
        return new ArrayList<>(getMap().keySet());
    }

}
