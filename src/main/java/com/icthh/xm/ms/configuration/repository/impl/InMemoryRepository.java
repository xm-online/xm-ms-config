package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RequiredArgsConstructor
@Component
public class InMemoryRepository implements DistributedConfigRepository {

    private final AtomicReference<String> currentCommit = new AtomicReference<>();
    private final ConcurrentMap<String, Configuration> storage = new ConcurrentHashMap<>();
    private final PersistenceConfigRepository persistenceConfigRepository;
    private final ConfigTopicProducer configTopicProducer;

    @Override
    public Map<String, Configuration> getMap(String commit) {
        if (StringUtils.isEmpty(commit)
            || (currentCommit.get() != null && commit.equals(currentCommit.get()))) {
            return storage;
        } else {
            refreshAll();
            return storage;
        }
    }

    @Override
    public List<Configuration> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Configuration find(String path) {
        log.debug("Get configuration from memory by path {}", path);
        return getMap(null).get(path);
    }

    @Override
    public String save(Configuration configuration) {
        return save(configuration, null);
    }

    @Override
    public String save(Configuration configuration, String oldConfigHash) {
        String commit = persistenceConfigRepository.save(configuration, oldConfigHash);

        getMap(null).put(configuration.getPath(), configuration);
        currentCommit.set(commit);
        configTopicProducer.notifyConfigurationChanged(configuration.getCommit(), singletonList(configuration.getPath()));
        return commit;
    }

    @Override
    public String saveAll(List<Configuration> configurations) {
        String commit = persistenceConfigRepository.saveAll(configurations);

        Map<String, Configuration> map = new HashMap<>();
        configurations.forEach(configuration -> map.put(configuration.getPath(), configuration));
        getMap(null).putAll(map);
        currentCommit.set(commit);
        configTopicProducer.notifyConfigurationChanged(commit, configurations.stream()
            .map(Configuration::getPath).collect(toList()));
        return commit;
    }

    @Override
    public String deleteAll(List<String> paths) {
        String commit = persistenceConfigRepository.deleteAll(paths);

        paths.forEach(path -> getMap(null).remove(path));
        currentCommit.set(commit);
        configTopicProducer.notifyConfigurationChanged(null, paths);
        return commit;
    }

    @Override
    public String delete(String path) {
        String commit = persistenceConfigRepository.delete(path);

        getMap(null).remove(path);
        currentCommit.set(commit);
        configTopicProducer.notifyConfigurationChanged(null, singletonList(path));
        return commit;
    }

    @Override
    public void refreshAll() {
        List<Configuration> actualConfigs = persistenceConfigRepository.findAll();
        Set<String> oldKeys = getMap(null).keySet();
        actualConfigs.forEach(config -> oldKeys.remove(config.getPath()));
        deleteAll(new ArrayList<>(oldKeys));
        saveAll(actualConfigs);
    }

    @Override
    public void refreshPath(String path) {
        Configuration configuration = persistenceConfigRepository.find(path);
        save(configuration);
    }

    @Override
    public void refreshTenant(String tenant) {
        List<Configuration> actualConfigs = persistenceConfigRepository.findAll();
        actualConfigs = actualConfigs.stream()
            .filter(config -> config.getPath().startsWith(getTenantPathPrefix(tenant)))
            .collect(toList());

        Set<String> oldKeys = getMap(null).keySet()
            .stream()
            .filter(path -> path.startsWith(getTenantPathPrefix(tenant)))
            .collect(toSet());

        actualConfigs.forEach(config -> oldKeys.remove(config.getPath()));
        deleteAll(new ArrayList<>(oldKeys));
        saveAll(actualConfigs);
    }
}
