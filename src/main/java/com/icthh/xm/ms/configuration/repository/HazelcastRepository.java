package com.icthh.xm.ms.configuration.repository;

import static com.icthh.xm.ms.configuration.config.HazelcastConfiguration.TENANT_CONFIGURATION_HAZELCAST;
import static com.icthh.xm.ms.configuration.config.HazelcastConfiguration.TENANT_CONFIGURATION_MAP;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.icthh.xm.ms.configuration.domain.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HazelcastRepository {

    private final HazelcastInstance hazelcast;

    public HazelcastRepository(@Qualifier(TENANT_CONFIGURATION_HAZELCAST) HazelcastInstance hazelcast) {
        this.hazelcast = hazelcast;
    }

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

    public void save(Configuration configuration) {
        log.info("Save configuration to hazelcast with path {}", configuration.getPath());
        getMap().put(configuration.getPath(), configuration.getContent());
    }

    public void saveAll(List<Configuration> configurations) {
        log.info("Save configuration to hazelcast with path {}", configurations);
        Map<String, String> map = new HashMap<>();
        configurations.forEach(c -> map.put(c.getPath(), c.getContent()));
        getMap().putAll(map);
    }

    public void delete(String path) {
        log.info("Delete configuration from hazelcast by path {}", path);
        getMap().remove(path);
    }

}
