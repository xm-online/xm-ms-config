package com.icthh.xm.ms.configuration.repository.impl;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.icthh.xm.commons.config.domain.Configuration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// immutable
public class ConfigState {
    @Getter
    private final String key;
    private final Map<String, Configuration> persistedConfigurations;
    @Getter
    private final Map<String, Configuration> inmemoryConfigurations; // configuration with tenant alias and with features
    private final Map<String, Configuration> processedConfiguration;
    // to identify which processed configuration created by processing of which configuration
    private final Map<String, Set<String>> producedByFile;

    public ConfigState(String key) {
        this.key = key;
        this.persistedConfigurations = Map.of();
        this.inmemoryConfigurations = Map.of();
        this.processedConfiguration = Map.of();
        this.producedByFile = Map.of();
    }

    public ConfigState(IntermediateConfigState state) {
        this.key = state.key;
        this.persistedConfigurations = Map.copyOf(state.persistedConfigurations);
        this.inmemoryConfigurations = Map.copyOf(state.inmemoryConfigurations);
        this.processedConfiguration = Map.copyOf(state.processedConfiguration);
        this.producedByFile = Map.copyOf(state.producedByFile);
    }

    public Map<String, Configuration> getProcessedConfiguration() {
        Map<String, Configuration> result = new HashMap<>();
        result.putAll(inmemoryConfigurations);
        result.putAll(processedConfiguration);
        return result;
    }

    public List<Configuration> calculateDeleted(List<Configuration> actualConfigs) {
        Set<String> actualPaths = actualConfigs.stream().map(Configuration::getPath).collect(toSet());
        Set<String> existingPaths = new HashSet<>(persistedConfigurations.keySet());
        existingPaths.removeAll(actualPaths);
        return existingPaths.stream().map(it -> new Configuration(it, "")).collect(toList());
    }

    public IntermediateConfigState toIntermediateConfigState() {
        return new IntermediateConfigState(
            key,
            new HashMap<>(persistedConfigurations),
            new HashMap<>(inmemoryConfigurations),
            new HashMap<>(processedConfiguration),
            new HashMap<>(producedByFile)
        );
    }

    public Set<String> pathsByFolder(Collection<String> paths) {
        return paths.stream().flatMap(path -> persistedConfigurations.keySet().stream()
            .filter(it -> it.equals(path) || it.startsWith(normalizedPath(path)))
        ).collect(toSet());
    }

    private static String normalizedPath(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    // mutable
    @Getter
    @RequiredArgsConstructor
    public static class IntermediateConfigState {
        private final String key;
        private final Map<String, Configuration> persistedConfigurations;
        private final Map<String, Configuration> inmemoryConfigurations;
        private final Map<String, Configuration> processedConfiguration;
        private final Map<String, Set<String>> producedByFile;

        private final Map<String, Configuration> changedFiles = new HashMap<>();

        public void updateConfigurations(Map<String, Configuration> updatedConfigs) {
            persistedConfigurations.putAll(updatedConfigs);

            inmemoryConfigurations.putAll(updatedConfigs);

            persistedConfigurations.entrySet().removeIf(entry -> isBlank(entry.getValue().getContent()));
            inmemoryConfigurations.entrySet().removeIf(entry -> isBlank(entry.getValue().getContent()));

            changedFiles.putAll(updatedConfigs);
        }

        public void addParentConfigurationByAliases(Map<String, Configuration> parentConfigs) {
            Map<String, Configuration> configsToApply = new HashMap<>(parentConfigs);
            configsToApply.keySet().removeAll(persistedConfigurations.keySet()); // "override" parent configurations
            inmemoryConfigurations.putAll(configsToApply); // put parent configurations
            inmemoryConfigurations.entrySet().removeIf(entry -> isBlank(entry.getValue().getContent()));

            changedFiles.putAll(configsToApply);
        }

        public void cleanProcessedConfiguration(Collection<String> paths) {
            paths.forEach(path -> {
                producedByFile.getOrDefault(path, Set.of()).forEach(processedConfiguration::remove);
                producedByFile.remove(path);
            });
        }

        public void addProcessedConfiguration(Configuration configuration, Map<String, Configuration> processedByConfig) {
            if (!processedByConfig.isEmpty()) {
                Set<String> produced = producedByFile.computeIfAbsent(configuration.getPath(), it -> new HashSet<>());
                produced.addAll(processedByConfig.keySet());
                processedConfiguration.putAll(processedByConfig);
            }
        }
    }

}
