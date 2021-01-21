package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.service.processors.ConfigurationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static java.util.Collections.singletonList;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Slf4j
@RequiredArgsConstructor
public class MemoryConfigStorage {

    private final ConcurrentMap<String, Configuration> storage = new ConcurrentHashMap<>();
    /** use for processed config with private information (returned only by /api/private) see ConfigMapResource */
    private final ConcurrentMap<String, Configuration> privateStorage = new ConcurrentHashMap<>();
    /** use for processed configs for override */
    private final ConcurrentMap<String, Configuration> processedStorage = new ConcurrentHashMap<>();

    private final List<ConfigurationProcessor> configurationProcessors;

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

    public boolean removeConfig(String path) {
        boolean removed = storage.remove(path) != null;
        removed = removed || processedStorage.remove(path) != null;
        removed = removed || privateStorage.remove(path) != null;
        return removed;
    }

    private void process(Configuration configuration) {
        configurationProcessors.stream()
                .filter(not(ConfigurationProcessor::isPrivate))
                .forEach(processor -> {
                    processor.processToConfigurations(configuration)
                            .forEach(config -> processedStorage.put(config.getPath(), config));
                });
        configurationProcessors.stream()
                .filter(ConfigurationProcessor::isPrivate)
                .forEach(processor -> {
                    processor.processToConfigurations(configuration)
                            .forEach(config -> privateStorage.put(config.getPath(), config));
                });
    }

    public void updateConfigs(Map<String, Configuration> map) {
        map.forEach(this::updateConfig);
    }
}
