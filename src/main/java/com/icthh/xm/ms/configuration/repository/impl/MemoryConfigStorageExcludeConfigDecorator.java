package com.icthh.xm.ms.configuration.repository.impl;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.util.AntPathMatcher;

@RequiredArgsConstructor
public class MemoryConfigStorageExcludeConfigDecorator implements MemoryConfigStorage {

    private final MemoryConfigStorage memoryConfigStorage;
    private final ApplicationProperties applicationProperties;
    private final AntPathMatcher matcher = new AntPathMatcher();

    private boolean isExcludedConfig(String path) {
        if (isEmpty(applicationProperties.getExcludeConfigPatterns())) {
            return false;
        }
        return applicationProperties.getExcludeConfigPatterns().stream().anyMatch(it -> matcher.match(it, path));
    }

    private boolean isExcludedConfig(Configuration configuration) {
        return isExcludedConfig(configuration.getPath());
    }

    private List<Configuration> filterExcludedConfig(Collection<Configuration> configurations) {
        return configurations.stream().filter(not(this::isExcludedConfig)).collect(toList());
    }

    private List<String> filterExcludedPaths(Collection<String> configurations) {
        return configurations.stream().filter(not(this::isExcludedConfig)).collect(toList());
    }

    @Override
    public Set<String> remove(Collection<String> configs) {
        configs = filterExcludedPaths(configs);
        return memoryConfigStorage.remove(configs);
    }

    @Override
    public Set<String> saveConfigs(List<Configuration> configs) {
        configs = filterExcludedConfig(configs);
        return memoryConfigStorage.saveConfigs(configs);
    }

    @Override
    public Set<String> replaceByConfiguration(List<Configuration> actualConfigs) {
        actualConfigs = filterExcludedConfig(actualConfigs);
        return memoryConfigStorage.replaceByConfiguration(actualConfigs);
    }

    @Override
    public Set<String> replaceByConfigurationInTenant(List<Configuration> actualConfigs, String tenant) {
        actualConfigs = filterExcludedConfig(actualConfigs);
        return memoryConfigStorage.replaceByConfigurationInTenant(actualConfigs, tenant);
    }

    @Override
    public Set<String> replaceByConfigurationInTenants(List<Configuration> actualConfigs, List<String> tenants) {
        actualConfigs = filterExcludedConfig(actualConfigs);
        return memoryConfigStorage.replaceByConfigurationInTenants(actualConfigs, tenants);
    }

    @Override
    public Map<String, Configuration> getProcessedConfigs() {
        return memoryConfigStorage.getProcessedConfigs();
    }

    @Override
    public Map<String, Configuration> getProcessedConfigs(Collection<String> paths) {
        return memoryConfigStorage.getProcessedConfigs(paths);
    }

    @Override
    public Optional<Configuration> getProcessedConfig(String path) {
        return memoryConfigStorage.getProcessedConfig(path);
    }

    @Override
    public Optional<Configuration> getConfig(String path) {
        return memoryConfigStorage.getConfig(path);
    }

    @Override
    public List<Configuration> getConfigs(Collection<String> path) {
        return memoryConfigStorage.getConfigs(path);
    }

    @Override
    public List<Configuration> getConfigsFromTenant(String tenant) {
        return memoryConfigStorage.getConfigsFromTenant(tenant);
    }
}
