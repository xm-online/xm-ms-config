package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree.TenantAlias;
import com.icthh.xm.ms.configuration.service.TenantAliasService;
import com.icthh.xm.ms.configuration.service.processors.ConfigurationProcessor;
import com.icthh.xm.ms.configuration.service.processors.PrivateConfigurationProcessor;
import com.icthh.xm.ms.configuration.service.processors.PublicConfigurationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.thymeleaf.util.SetUtils.singletonSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryConfigStorage {

    /** original configuration in memory storage */
    private final ConcurrentMap<String, Configuration> storage = new ConcurrentHashMap<>();
    /** use for processed config with private information (returned only by /api/private) see ConfigMapResource */
    private final ConcurrentMap<String, Configuration> privateStorage = new ConcurrentHashMap<>();
    /** use for processed configs for override */
    private final ConcurrentMap<String, Configuration> processedStorage = new ConcurrentHashMap<>();

    private final List<PrivateConfigurationProcessor> privateConfigurationProcessors;
    private final List<PublicConfigurationProcessor> publicConfigurationProcessors;
    private final TenantAliasService tenantAliasService;

    public Map<String, Configuration> getPrivateConfigs() {
        Map<String, Configuration> configs = new HashMap<>();
        configs.putAll(storage);
        configs.putAll(processedStorage);
        configs.putAll(privateStorage);
        return configs;
    }

    public List<Configuration> getConfigList() {
        Map<String, Configuration> configs = new HashMap<>();
        configs.putAll(storage);
        configs.putAll(processedStorage);
        return new ArrayList<>(configs.values());
    }

    public Configuration getConfigByPath(String path) {
        return processedStorage.getOrDefault(path, storage.get(path));
    }

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

    public Set<String> getConfigPathsList(String tenant) {
        return getConfigPathsList()
                .stream()
                .filter(path -> path.startsWith(getTenantPathPrefix(tenant)))
                .collect(toSet());
    }

    public Set<String> getConfigPathsList() {
        Set<String> keys = new HashSet<>(storage.keySet());
        keys.addAll(processedStorage.keySet());
        keys.addAll(privateStorage.keySet());
        return keys;
    }

    public void updateConfig(String path, Configuration config) {
        storage.put(path, config);
        process(config);
    }

    public Set<String> refreshStorage(List<Configuration> actualConfigs, String tenant) {
        List<TenantAlias> parents = tenantAliasService.getTenantAliasTree().getParents(tenant);
        Set<String> oldKeys = getConfigPathsList(tenant);
        Set<String> updated = refreshStorage(actualConfigs, oldKeys);
        parents.stream().map(TenantAlias::getKey).forEach(this::reprocess);
        return updated;
    }

    private void reprocess(String tenant) {
        String tenantPathPrefix = getTenantPathPrefix(tenant);
        storage.keySet().stream()
                .filter(it -> it.startsWith(tenantPathPrefix))
                .map(storage::get)
                .forEach(this::process);
    }

    public Set<String> refreshStorage(List<Configuration> actualConfigs) {
        Set<String> oldKeys = getConfigPathsList();
        return refreshStorage(actualConfigs, oldKeys);
    }

    @Synchronized
    private Set<String> refreshStorage(List<Configuration> actualConfigs, Set<String> oldKeys) {
        Set<String> updated = getUpdatePaths(actualConfigs, oldKeys);
        actualConfigs.forEach(config -> oldKeys.remove(config.getPath()));
        oldKeys.forEach(this::removeConfig);
        actualConfigs.forEach(configuration -> updateConfig(configuration.getPath(), configuration));
        return updated;
    }

    private Set<String> getUpdatePaths(List<Configuration> actualConfigs, Set<String> oldKeys) {
        Set<String> updated = new HashSet<>(oldKeys.size() + actualConfigs.size());
        Set<String> actualConfigPaths = actualConfigs.stream().map(Configuration::getPath).collect(toSet());
        updated.addAll(actualConfigPaths);
        updated.addAll(oldKeys);
        return updated;
    }

    public boolean removeConfig(String path) {
        boolean removed = storage.remove(path) != null;
        removed = processedStorage.remove(path) != null || removed;
        removed = privateStorage.remove(path) != null || removed;
        return removed;
    }

    @SuppressWarnings("ConstantConditions")
    private void process(Configuration configuration) {
        Set<Configuration> configurations = singletonSet(configuration);
        configurations = processConfiguration(configurations, publicConfigurationProcessors, processedStorage);
        processConfiguration(configurations, privateConfigurationProcessors, privateStorage);
    }

    private Set<Configuration> processConfiguration(Set<Configuration> configurations,
                                                    List<? extends ConfigurationProcessor> configurationProcessors,
                                                    Map<String, Configuration> processedStorage) {
        Set<Configuration> currentConfigurations = new HashSet<>(configurations);
        for (var processor: configurationProcessors) {
            Set<Configuration> processedConfiguration = currentConfigurations.stream()
                    .filter(processor::isSupported)
                    .flatMap(runProcessor(processor, processedStorage))
                    .collect(toSet());

            currentConfigurations.addAll(processedConfiguration);
        }
        return currentConfigurations;
    }

    private Function<Configuration, Stream<Configuration>> runProcessor(ConfigurationProcessor processor,
                                                                        Map<String, Configuration> processedStorage) {
        var storage = unmodifiableMap(this.storage);
        return configuration -> {
            List<Configuration> configurations = processor.processConfiguration(configuration, storage, processedStorage);
            processedStorage.putAll(configurations.stream().collect(toMap(Configuration::getPath, identity())));
            return configurations.stream();
        };
    }

    public void updateConfigs(Map<String, Configuration> map) {
        map.forEach(this::updateConfig);
    }

    public Set<String> processedPaths() {
        return processedStorage.keySet();
    }

    public void clear() {
        storage.clear();
        processedStorage.clear();
        privateStorage.clear();
    }
}
