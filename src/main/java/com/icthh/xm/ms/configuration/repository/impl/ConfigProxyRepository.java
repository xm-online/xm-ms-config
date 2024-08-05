package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.utils.LockUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static com.icthh.xm.ms.configuration.config.BeanConfiguration.UPDATE_BY_COMMIT_LOCK;
import static com.icthh.xm.ms.configuration.domain.ConfigVersion.UNDEFINED_VERSION;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Slf4j
@Component
public class ConfigProxyRepository implements DistributedConfigRepository {
    @Getter(AccessLevel.PACKAGE)
    private final AtomicReference<ConfigVersion> version = new AtomicReference<>(UNDEFINED_VERSION);
    @Getter(AccessLevel.PACKAGE)
    private final MemoryConfigStorage storage;
    private final PersistenceConfigRepository persistenceConfigRepository;
    private final ConfigTopicProducer configTopicProducer;
    private final Lock lock;
    ApplicationProperties applicationProperties;

    public ConfigProxyRepository(MemoryConfigStorage storage,
                                 @Qualifier("configRepository")
                                 PersistenceConfigRepository persistenceConfigRepository,
                                 ConfigTopicProducer configTopicProducer,
                                 ApplicationProperties applicationProperties,
                                 @Qualifier(UPDATE_BY_COMMIT_LOCK)
                                 Lock lock) {
        this.storage = storage;
        this.persistenceConfigRepository = persistenceConfigRepository;
        this.configTopicProducer = configTopicProducer;
        this.applicationProperties = applicationProperties;
        this.lock = lock;
    }


    /**
     * Get internal map config. If commit is not specified, or commit is the same as inmemory,
     * or commit is older than inmemory - return from storage, else reload from git
     *
     * @param version required commit
     * @return config map
     */
    @Override
    public Map<String, Configuration> getMap(ConfigVersion version) {
        if (isOnCommit(version)) {
            log.info("Get configuration from memory by commit: {}", version);
        } else {
            updateConfig(version);
        }
        return storage.getPrivateConfigs();
    }

    private boolean isOnCommit(ConfigVersion version) {
        return  version == null || UNDEFINED_VERSION.equals(version)
            || version.equals(this.version.get())
            || persistenceConfigRepository.hasVersion(version);
    }

    private void updateConfig(ConfigVersion version) {
        LockUtils.runWithLock(lock, applicationProperties.getUpdateConfigWaitTimeSecond(), () -> {
            if (isOnCommit(version)) {
                log.info("Configuration already actual by commit: {}", version);
                return;
            }

            log.info("Load actual configuration from git by commit: {}", version);
            ConfigurationList configurationList = persistenceConfigRepository.findAll();
            List<Configuration> actualConfigs = configurationList.getData();
            storage.refreshStorage(actualConfigs);
            updateVersion(configurationList.getVersion());
        });
    }

    @Override
    public boolean hasVersion(ConfigVersion version) {
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
    public Configuration find(String path, ConfigVersion version) {
        if (version == null || UNDEFINED_VERSION.equals(version)){
            return find(path).getData();
        }
        log.debug("Get configuration from storage by path {} and version {}", path, version);
        return persistenceConfigRepository.find(path, version);
    }

    @Override
    public ConfigVersion save(Configuration configuration) {
        return save(configuration, null);
    }

    @Override
    public ConfigVersion save(Configuration configuration, String oldConfigHash) {
        ConfigVersion version = persistenceConfigRepository.save(configuration, oldConfigHash);
        updateConfigurationInMemory(configuration, version);
        return version;
    }

    @Override
    public void updateConfigurationInMemory(Configuration configuration, ConfigVersion commit) {
        Set<String> updated = storage.updateConfig(configuration.getPath(), configuration);
        updateVersion(commit);
        notifyChanged(commit, updated);
    }

    @Override
    public ConfigVersion saveAll(List<Configuration> configurations) {
        ConfigVersion commit = persistenceConfigRepository.saveAll(configurations);
        updateConfigurationsInMemory(configurations, commit);
        return commit;
    }

    @Override
    public ConfigVersion setRepositoryState(List<Configuration> configurations) {
        ConfigVersion commit = persistenceConfigRepository.setRepositoryState(configurations);
        refreshAll();
        return commit;
    }

    @Override
    public void updateConfigurationsInMemory(List<Configuration> configurations, ConfigVersion commit) {
        Set<String> updated = storage.updateConfigs(configurations);
        updateVersion(commit);
        notifyChanged(commit, updated);
    }

    @Override
    public ConfigVersion delete(String path) {
        ConfigVersion commit = persistenceConfigRepository.delete(path);
        List<String> removedPaths = storage.removeExactOrByPrefix(path);
        updateVersion(commit);
        notifyChanged(commit, removedPaths);
        return commit;
    }

    @Override
    public ConfigVersion deleteAll(List<String> paths) {
        ConfigVersion commit = persistenceConfigRepository.deleteAll(paths);
        updateVersion(commit);
        deleteAllInMemory(paths, commit);
        return commit;
    }

    public void deleteAllInMemory(List<String> paths) {
        deleteAllInMemory(paths, getCurrentVersion());
    }

    private void deleteAllInMemory(List<String> paths, ConfigVersion commit) {
        Set<String> removed = paths.stream()
                                   .map(storage::removeExactOrByPrefix)
                                   .flatMap(List::stream)
                                   .collect(toSet());
        notifyChanged(commit, removed);
    }

    @Override
    public void refreshInternal() {
        ConfigurationList configurationList = persistenceConfigRepository.findAll();
        List<Configuration> actualConfigs = configurationList.getData();
        storage.refreshStorage(actualConfigs);
        updateVersion(configurationList.getVersion());
    }

    @Override
    public void refreshAll() {
        refreshAll(List.of());
    }

    @Override
    public void refreshAll(List<String> excludeNotificationPaths) {
        ConfigurationList configurationList = persistenceConfigRepository.findAll();
        List<Configuration> actualConfigs = configurationList.getData();
        Set<String> updated = storage.refreshStorage(actualConfigs);
        updateVersion(configurationList.getVersion());
        excludeNotificationPaths.forEach(updated::remove);
        notifyChanged(updated);
    }

    @Override
    public void recloneConfiguration() {
        persistenceConfigRepository.recloneConfiguration();
        refreshAll();
    }

    @Override
    public void refreshPath(String path) {
        ConfigurationItem configurationItem = persistenceConfigRepository.find(path);
        Configuration configuration = configurationItem.getData();
        storage.updateConfig(configuration.getPath(), configuration);
        notifyChanged(configurationItem.getVersion(), singletonList(configuration.getPath()));
    }

    @Override
    public void refreshTenant(String tenant, ConfigurationList configs) {
        ConfigurationList configurationList = configs;
        if (configurationList == null) {
            configurationList = persistenceConfigRepository.findAll();
        }
        List<Configuration> actualConfigs = configurationList.getData().stream()
            .filter(config -> config.getPath().startsWith(getTenantPathPrefix(tenant)))
            .collect(toList());

        Set<String> updated = storage.refreshStorage(actualConfigs, tenant);
        updated.addAll(storage.getConfigPathsList(tenant));
        updateVersion(configurationList.getVersion());
        notifyChanged(updated);
    }

    @Override
    public void refreshTenant(String tenant) {
        refreshTenant(tenant, null);
    }

    @Override
    public ConfigVersion saveOrDeleteEmpty(List<Configuration> configurations) {
        return persistenceConfigRepository.saveOrDeleteEmpty(configurations);
    }

    @Override
    public ConfigVersion getCurrentVersion() {
        return version.get();
    }

    private void updateVersion(ConfigVersion commit) {
        version.set(commit);
    }

    private void notifyChanged(Set<String> updated) {
        notifyChanged(version.get(), updated);
    }

    private void notifyChanged(ConfigVersion commit, Collection<String> updated) {
        configTopicProducer.notifyConfigurationChanged(commit, new ArrayList<>(updated));
    }
}
