package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface MemoryConfigStorage {

    Map<String, Configuration> getProcessedConfigs();
    Map<String, Configuration> getProcessedConfigs(Collection<String> paths);
    Map<String, Configuration> getProcessedAntPatternConfigs(Collection<String> paths);
    Optional<Configuration> getProcessedConfig(String path);

    Optional<Configuration> getConfig(String path);
    List<Configuration> getConfigs(String tenant, Collection<String> path);
    List<Configuration> getConfigsFromTenant(String tenant);

    Set<String> getTenants();

    Optional<Set<Configuration>> getExternalConfig(String configKey);

    Set<String> saveConfigs(List<Configuration> configs);
    Set<String> remove(Collection<String> paths);

    Set<String> replaceByConfiguration(List<Configuration> actualConfigs);
    Set<String> replaceByConfigurationInTenant(List<Configuration> actualConfigs, String tenant);
    Set<String> replaceByConfigurationInTenants(List<Configuration> actualConfigs, List<String> tenants);

    default void clear() {
        replaceByConfiguration(List.of());
    }

}
