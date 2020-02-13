package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.service.processors.ConfigurationProcessor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RequiredArgsConstructor
@Component
public class ConfigProxyRepository implements DistributedConfigRepository {
    @Getter(AccessLevel.PACKAGE)
    private final AtomicReference<String> version = new AtomicReference<>();
    @Getter(AccessLevel.PACKAGE)
    private final ConcurrentMap<String, Configuration> storage = new ConcurrentHashMap<>();
    private final PersistenceConfigRepository persistenceConfigRepository;
    private final ConfigTopicProducer configTopicProducer;
    private final List<ConfigurationProcessor> configurationProcessors;

    /**
     * Get internal map config. If commit is not specified, or commit is the same as inmemory,
     * or commit is older than inmemory - return from storage, else reload from git
     *
     * @param commit required commit
     * @return config map
     */
    @Override
    public Map<String, Configuration> getMap(String commit) {
        if (StringUtils.isEmpty(commit)
            || (version.get() != null && commit.equals(version.get()))
            || persistenceConfigRepository.hasVersion(commit)) {
            log.debug("Get configuration from memory by commit: {}", commit);
            return storage;
        } else {
            ConfigurationList configurationList = persistenceConfigRepository.findAll();
            List<Configuration> actualConfigs = configurationList.getData();
            Set<String> oldKeys = new HashSet<>(storage.keySet());
            refreshStorage(actualConfigs, oldKeys);
            updateVersion(configurationList.getCommit());
            return storage;
        }
    }

    @Override
    public boolean hasVersion(String version) {
        throw new NotImplementedException("hasVersion() not implemented for ConfigProxyRepository");
    }

    @Override
    public ConfigurationList findAll() {
        log.debug("Get configuration from memory all");
        return new ConfigurationList(version.get(), new ArrayList<>(storage.values()));
    }

    @Override
    public ConfigurationItem find(String path) {
        log.debug("Get configuration from memory by path {}", path);
        return new ConfigurationItem(version.get(), storage.get(path));
    }

    @Override
    public ConfigurationItem find(String path, String version) {
        if (version == null) {
            return find(path);
        }
        log.debug("Get configuration from storage by path {} and version {}", path, version);
        return persistenceConfigRepository.find(path, version);
    }

    @Override
    public String save(Configuration configuration) {
        return save(configuration, null);
    }

    @Override
    public String save(Configuration configuration, String oldConfigHash) {
        String commit = persistenceConfigRepository.save(configuration, oldConfigHash);
        updateConfigurationInMemory(configuration, commit);
        return commit;
    }

    @Override
    public void updateConfigurationInMemory(Configuration configuration, String commit) {
        storage.put(configuration.getPath(), process(configuration));
        version.set(commit);
        configTopicProducer.notifyConfigurationChanged(commit, singletonList(configuration.getPath()));
    }

    @Override
    public String saveAll(List<Configuration> configurations) {
        String commit = persistenceConfigRepository.saveAll(configurations);
        updateConfigurationsInMemory(configurations, commit);
        return commit;
    }

    @Override
    public void updateConfigurationsInMemory(List<Configuration> configurations, String commit) {
        Map<String, Configuration> map = new HashMap<>();
        configurations.forEach(configuration -> map.put(configuration.getPath(), process(configuration)));
        storage.putAll(map);
        version.set(commit);
        configTopicProducer.notifyConfigurationChanged(commit, configurations.stream()
            .map(Configuration::getPath).collect(toList()));
    }

    @Override
    public String delete(String path) {
        String commit = persistenceConfigRepository.delete(path);
        List<String> removedPaths = removeExactOrByPrefix(path);
        version.set(commit);
        configTopicProducer.notifyConfigurationChanged(commit, removedPaths);
        return commit;
    }

    @Override
    public String deleteAll(List<String> paths) {
        String commit = persistenceConfigRepository.deleteAll(paths);
        version.set(commit);
        deleteAllInMemory(paths, commit);
        return commit;
    }

    public void deleteAllInMemory(List<String> paths) {
        deleteAllInMemory(paths, getCommitVersion());
    }

    private void deleteAllInMemory(List<String> paths, String commit) {
        Set<String> removed = paths.stream()
                                   .map(this::removeExactOrByPrefix)
                                   .flatMap(List::stream)
                                   .collect(toSet());
        configTopicProducer.notifyConfigurationChanged(commit, new LinkedList<>(removed));
    }

    @Override
    public void refreshInternal() {
        ConfigurationList configurationList = persistenceConfigRepository.findAll();
        List<Configuration> actualConfigs = configurationList.getData();
        Set<String> oldKeys = new HashSet<>(storage.keySet());
        refreshStorage(actualConfigs, oldKeys);
        updateVersion(configurationList.getCommit());
    }

    @Override
    public void refreshAll() {
        ConfigurationList configurationList = persistenceConfigRepository.findAll();
        List<Configuration> actualConfigs = configurationList.getData();
        Set<String> oldKeys = new HashSet<>(storage.keySet());

        refreshStorage(actualConfigs, oldKeys);
        updateVersion(configurationList.getCommit());
        notifyChanged(actualConfigs, oldKeys);
    }

    @Override
    public void refreshPath(String path) {
        ConfigurationItem configurationItem = persistenceConfigRepository.find(path);
        Configuration configuration = configurationItem.getData();
        storage.put(configuration.getPath(), process(configuration));
        configTopicProducer.notifyConfigurationChanged(configurationItem.getCommit(), singletonList(configuration.getPath()));
    }

    @Override
    public void refreshTenant(String tenant) {
        ConfigurationList configurationList = persistenceConfigRepository.findAll();
        List<Configuration> actualConfigs = configurationList.getData();
        actualConfigs = actualConfigs.stream()
            .filter(config -> config.getPath().startsWith(getTenantPathPrefix(tenant)))
            .collect(toList());

        Set<String> oldKeys = storage.keySet()
            .stream()
            .filter(path -> path.startsWith(getTenantPathPrefix(tenant)))
            .collect(toSet());

        refreshStorage(actualConfigs, oldKeys);
        notifyChanged(actualConfigs, oldKeys);
    }

    @Override
    public String getCommitVersion() {
        return version.get();
    }

    private List<String> removeExactOrByPrefix(final String path) {
        Configuration removed = storage.remove(path);
        if (removed == null) {
            List<String> subPaths = storage.keySet()
                                           .stream()
                                           .filter(key -> key.startsWith(path))
                                           .collect(toList());
            if (!subPaths.isEmpty()) {
                log.warn("Remove all sub-paths of [{}]: {}", path, subPaths);
                subPaths.forEach(storage::remove);
            }
            return subPaths;
        }
        return singletonList(path);
    }

    @Synchronized
    private void refreshStorage(List<Configuration> actualConfigs, Set<String> oldKeys) {
        actualConfigs.forEach(config -> oldKeys.remove(config.getPath()));
        oldKeys.forEach(storage::remove);
        actualConfigs.forEach(configuration -> storage.put(configuration.getPath(), process(configuration)));
    }

    private void updateVersion(String commit) {
        version.set(commit);
    }

    private void notifyChanged(List<Configuration> actualConfigs, Set<String> oldKeys) {
        Set<String> updated = new HashSet<>(oldKeys.size() + actualConfigs.size());
        updated.addAll(actualConfigs.stream().map(Configuration::getPath).collect(toSet()));
        updated.addAll(oldKeys);
        configTopicProducer.notifyConfigurationChanged(version.get(), new ArrayList<>(updated));
    }

    private Configuration process(Configuration configuration) {
        for (ConfigurationProcessor processor : configurationProcessors) {
            configuration = processor.processConfiguration(configuration);
        }
        return configuration;
    }
}
