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
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigProxyRepository implements DistributedConfigRepository {
    @Getter(AccessLevel.PACKAGE)
    private final AtomicReference<String> version = new AtomicReference<>();
    @Getter(AccessLevel.PACKAGE)
    private final MemoryConfigStorage storage;
    private final PersistenceConfigRepository persistenceConfigRepository;
    private final ConfigTopicProducer configTopicProducer;

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
            return storage.getPrivateConfigs();
        } else {
            ConfigurationList configurationList = persistenceConfigRepository.findAll();
            List<Configuration> actualConfigs = configurationList.getData();
            storage.refreshStorage(actualConfigs);
            updateVersion(configurationList.getCommit());
            return storage.getPrivateConfigs();
        }
    }

    @Override
    public boolean hasVersion(String version) {
        throw new NotImplementedException("hasVersion() not implemented for ConfigProxyRepository");
    }

    @Override
    public ConfigurationList findAll() {
        log.debug("Get configuration from memory all");
        return new ConfigurationList(version.get(), new ArrayList<>(storage.getConfigList()));
    }

    @Override
    public ConfigurationItem find(String path) {
        log.debug("Get configuration from memory by path {}", path);
        return new ConfigurationItem(version.get(), storage.getConfigByPath(path));
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
        storage.updateConfig(configuration.getPath(), configuration);
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
        configurations.forEach(configuration -> map.put(configuration.getPath(), configuration));
        storage.updateConfigs(map);
        version.set(commit);
        configTopicProducer.notifyConfigurationChanged(commit, configurations.stream()
            .map(Configuration::getPath).collect(toList()));
    }

    @Override
    public String delete(String path) {
        String commit = persistenceConfigRepository.delete(path);
        List<String> removedPaths = storage.removeExactOrByPrefix(path);
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
                                   .map(storage::removeExactOrByPrefix)
                                   .flatMap(List::stream)
                                   .collect(toSet());
        configTopicProducer.notifyConfigurationChanged(commit, new LinkedList<>(removed));
    }

    @Override
    public void refreshInternal() {
        ConfigurationList configurationList = persistenceConfigRepository.findAll();
        List<Configuration> actualConfigs = configurationList.getData();
        storage.refreshStorage(actualConfigs);
        updateVersion(configurationList.getCommit());
    }

    @Override
    public void refreshAll() {
        ConfigurationList configurationList = persistenceConfigRepository.findAll();
        List<Configuration> actualConfigs = configurationList.getData();
        Set<String> updated = storage.refreshStorage(actualConfigs);
        updateVersion(configurationList.getCommit());
        notifyChanged(updated);
    }

    @Override
    public void recloneConfiguration() {
        persistenceConfigRepository.recloneConfiguration();
    }

    @Override
    public void refreshPath(String path) {
        ConfigurationItem configurationItem = persistenceConfigRepository.find(path);
        Configuration configuration = configurationItem.getData();
        storage.updateConfig(configuration.getPath(), configuration);
        configTopicProducer.notifyConfigurationChanged(configurationItem.getCommit(), singletonList(configuration.getPath()));
    }

    @Override
    public void refreshTenant(String tenant) {
        ConfigurationList configurationList = persistenceConfigRepository.findAll();
        List<Configuration> actualConfigs = configurationList.getData();
        actualConfigs = actualConfigs.stream()
            .filter(config -> config.getPath().startsWith(getTenantPathPrefix(tenant)))
            .collect(toList());

        Set<String> updated = storage.refreshStorage(actualConfigs, tenant);
        notifyChanged(updated);
    }

    @Override
    public String getCommitVersion() {
        return version.get();
    }

    private void updateVersion(String commit) {
        version.set(commit);
    }

    private void notifyChanged(Set<String> updated) {
        configTopicProducer.notifyConfigurationChanged(version.get(), new ArrayList<>(updated));
    }
}
