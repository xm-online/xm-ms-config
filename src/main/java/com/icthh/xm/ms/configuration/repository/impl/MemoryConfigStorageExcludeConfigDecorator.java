package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.util.AntPathMatcher;

import static java.util.Collections.emptySet;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;

@RequiredArgsConstructor
public class MemoryConfigStorageExcludeConfigDecorator implements MemoryConfigStorage {

    @Delegate(excludes = UpdateConfig.class)
    private final MemoryConfigStorage memoryConfigStorage;
    private final ApplicationProperties applicationProperties;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public Set<String> updateConfig(String path, Configuration config) {
        if (isExcludedConfig(path)) {
            return emptySet();
        }

        return memoryConfigStorage.updateConfig(path, config);
    }

    public Set<String> updateConfigs(List<Configuration> configs) {
        configs = filterExcludedConfig(configs);
        return memoryConfigStorage.updateConfigs(configs);
    }

    public Set<String> refreshStorage(List<Configuration> actualConfigs) {
        actualConfigs = filterExcludedConfig(actualConfigs);
        return memoryConfigStorage.refreshStorage(actualConfigs);
    }

    public Set<String> refreshStorage(List<Configuration> actualConfigs, String tenant) {
        actualConfigs = filterExcludedConfig(actualConfigs);
        return memoryConfigStorage.refreshStorage(actualConfigs, tenant);
    }

    private boolean isExcludedConfig(String path) {
        if (isEmpty(applicationProperties.getExcludeConfigPatterns())) {
            return false;
        }
        return applicationProperties.getExcludeConfigPatterns().stream().anyMatch(it -> matcher.match(it, path));
    }

    private boolean isExcludedConfig(Configuration configuration) {
        return isExcludedConfig(configuration.getPath());
    }

    private List<Configuration> filterExcludedConfig(List<Configuration> configurations) {
        return configurations.stream().filter(not(this::isExcludedConfig)).collect(toList());
    }

    private interface UpdateConfig {
        Set<String> updateConfig(String path, Configuration config);
        Set<String> updateConfigs(List<Configuration> configs);
        Set<String> refreshStorage(List<Configuration> actualConfigs);
        Set<String> refreshStorage(List<Configuration> actualConfigs, String tenant);
    }
}
