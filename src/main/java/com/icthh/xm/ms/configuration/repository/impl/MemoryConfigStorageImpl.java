package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree.TenantAlias;
import com.icthh.xm.ms.configuration.service.TenantAliasService;
import com.icthh.xm.ms.configuration.service.processors.ConfigurationProcessor;
import com.icthh.xm.ms.configuration.service.processors.PrivateConfigurationProcessor;
import com.icthh.xm.ms.configuration.service.processors.PublicConfigurationProcessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.thymeleaf.util.SetUtils.singletonSet;

@Slf4j
@RequiredArgsConstructor
public class MemoryConfigStorageImpl implements MemoryConfigStorage {

    /** original configuration in memory storage */
    private final ConcurrentMap<String, Configuration> storage = new ConcurrentHashMap<>();
    /** use for processed config with private information (returned only by /api/private) see ConfigMapResource */
    private final ConcurrentMap<String, Configuration> privateStorage = new ConcurrentHashMap<>();
    /** use for processed configs for override */
    private final ConcurrentMap<String, Configuration> processedStorage = new ConcurrentHashMap<>();
    /** use for retrieval of the private configs with overrides to avoid race conditions and configs inconsistency during config updates */
    private final ConcurrentMap<String, Configuration> privateConfigSnapshot = new ConcurrentHashMap<>();
    /** use for retrival of the processed config with private information to avoid race conditions and configs inconsistency during config updates */
    private final ConcurrentMap<String, Configuration> processedConfigSnapshot = new ConcurrentHashMap<>();

    private final List<PrivateConfigurationProcessor> privateConfigurationProcessors;
    private final List<PublicConfigurationProcessor> publicConfigurationProcessors;
    private final TenantAliasService tenantAliasService;

    @Override
    public Map<String, Configuration> getPrivateConfigs() {
        Map<String, Configuration> configs = new HashMap<>();
        configs.putAll(storage);
        configs.putAll(processedConfigSnapshot);
        configs.putAll(privateConfigSnapshot);
        return configs;
    }

    @Override
    public List<Configuration> getConfigList() {
        Map<String, Configuration> configs = new HashMap<>();
        configs.putAll(storage);
        configs.putAll(processedConfigSnapshot);
        return new ArrayList<>(configs.values());
    }

    @Override
    public Configuration getConfigByPath(String path) {
        return processedConfigSnapshot.getOrDefault(path, storage.get(path));
    }

    @Override
    public List<String> removeExactOrByPrefix(final String path) {
        boolean removed = removeConfig(path);
        if (!removed) {
            List<String> subPaths = getConfigPathsList()
                    .stream()
                    .filter(key -> key.startsWith(path))
                    .collect(toList());
            if (!subPaths.isEmpty()) {
                log.warn("Remove all sub-paths of [{}]: {}", path, subPaths);
                subPaths.forEach(this::removeConfig);
            }
            return subPaths;
        }
        return singletonList(path);
    }

    @Override
    public Set<String> getConfigPathsList(String tenant) {
        return getConfigPathsList()
                .stream()
                .filter(path -> path.startsWith(getTenantPathPrefix(tenant)))
                .collect(toSet());
    }

    private Set<String> getConfigPathsList() {
        Set<String> keys = new HashSet<>(storage.keySet());
        keys.addAll(processedStorage.keySet());
        keys.addAll(privateStorage.keySet());
        return keys;
    }

    @Override
    public Set<String> updateConfig(String path, Configuration config) {
        try {
            storage.put(path, config);
            return process(config);
        }finally {
            syncSnapshots();
        }
    }

    @Override
    public Set<String> refreshStorage(List<Configuration> actualConfigs, String tenant) {
        Set<String> oldKeys = getConfigPathsList(tenant);
        Set<String> updated = refreshStorage(actualConfigs, oldKeys);
        reprocess(tenant);
        return updated;
    }

    @Override
    public void reprocess(String tenant) {
        List<TenantAlias> parents = tenantAliasService.getTenantAliasTree().getParents(tenant);
        parents.stream().map(TenantAlias::getKey).forEach(this::reprocessTenant);
    }

    private void reprocessTenant(String tenant) {
        try {
            String tenantPathPrefix = getTenantPathPrefix(tenant);
            storage.keySet().stream()
                .filter(it -> it.startsWith(tenantPathPrefix))
                .map(storage::get)
                .forEach(this::process);
        }finally {
            syncSnapshots();
        }
    }

    @Override
    public Set<String> refreshStorage(List<Configuration> actualConfigs) {
        Set<String> oldKeys = getConfigPathsList();
        return refreshStorage(actualConfigs, oldKeys);
    }

    @Synchronized
    private Set<String> refreshStorage(List<Configuration> actualConfigs, Set<String> oldKeys) {
        Set<String> updated = getUpdatePaths(actualConfigs, oldKeys);
        actualConfigs.forEach(config -> oldKeys.remove(config.getPath()));
        oldKeys.forEach(this::removeConfig);
        updateConfigs(actualConfigs);
        return updated;
    }

    private Set<String> getUpdatePaths(List<Configuration> actualConfigs, Set<String> oldKeys) {
        Set<String> updated = new HashSet<>(oldKeys.size() + actualConfigs.size());
        Set<String> actualConfigPaths = actualConfigs.stream().map(Configuration::getPath).collect(toSet());
        updated.addAll(actualConfigPaths);
        updated.addAll(oldKeys);
        return updated;
    }

    @Override
    public boolean removeConfig(String path) {
        try {
            boolean removed = storage.remove(path) != null;
            removed = processedStorage.remove(path) != null || removed;
            removed = privateStorage.remove(path) != null || removed;
            return removed;
        }finally {
            syncSnapshots();
        }
    }

    @SuppressWarnings("ConstantConditions")
    private Set<String> process(Configuration configuration) {
        Set<Configuration> configurations = singletonSet(configuration);
        return processAll(configurations);
    }

    private Set<String> processAll(Set<Configuration> configurations) {
        configurations = processConfiguration(configurations, publicConfigurationProcessors, processedStorage);
        configurations = processConfiguration(configurations, privateConfigurationProcessors, privateStorage);
        return configurations.stream().filter(Objects::nonNull).map(Configuration::getPath).collect(toSet());
    }

    private Set<Configuration> processConfiguration(Set<Configuration> configurations,
                                                    List<? extends ConfigurationProcessor> configurationProcessors,
                                                    Map<String, Configuration> processedStorage) {
        Set<Configuration> currentConfigurations = new HashSet<>(configurations);
        Map<String, Configuration> resultStorage = new HashMap<>();
        Set<Configuration> configToReprocess = new HashSet<>();
        for (var processor: configurationProcessors) {
            Set<Configuration> processedConfiguration = currentConfigurations.stream()
                .filter(Objects::nonNull)
                .filter(processor::isSupported)
                .flatMap(runProcessor(processor, resultStorage, processedStorage, configToReprocess))
                .collect(toSet());

            currentConfigurations.addAll(processedConfiguration);
        }
        log.trace("Configs to reprocess: {}", configToReprocess);
        if (!configToReprocess.isEmpty()) {
            log.info("Need to reprocess {} configs", configToReprocess.size());
            processAll(configToReprocess);
        }
        processedStorage.putAll(resultStorage);
        return currentConfigurations;
    }

    private Function<Configuration, Stream<Configuration>> runProcessor(ConfigurationProcessor processor,
                                                                        Map<String, Configuration> resultStorage,
                                                                        Map<String, Configuration> processedStorage,
                                                                        Set<Configuration> configToReprocess) {
        var storage = unmodifiableMap(this.storage);
        return configuration -> {
            try {
                List<Configuration> configurations = processor.processConfiguration(configuration, storage, processedStorage, configToReprocess);
                resultStorage.putAll(configurations.stream().collect(toMap(Configuration::getPath, identity())));
                return configurations.stream();
            } catch (Exception e) {
                log.error("Error run processor", e);
            }
            return Stream.empty();
        };
    }

    @Override
    public Set<String> updateConfigs(List<Configuration> configs) {
        return configs.stream()
            .map(configuration -> updateConfig(configuration.getPath(),configuration))
            .flatMap(Collection::stream)
            .collect(toSet());
    }

    @Override
    public void clear() {
        storage.clear();
        processedStorage.clear();
        privateStorage.clear();
    }

    public void syncSnapshots() {
        privateConfigSnapshot.putAll(privateStorage);
        processedConfigSnapshot.putAll(processedStorage);
    }

}
