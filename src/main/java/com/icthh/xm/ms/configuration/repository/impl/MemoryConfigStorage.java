package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MemoryConfigStorage {

    Map<String, Configuration> getPrivateConfigs();

    List<Configuration> getConfigList();

    Configuration getConfigByPath(String path);

    List<String> removeExactOrByPrefix(String path);

    Set<String> getConfigPathsList(String tenant);

    void reprocess(String tenant);

    boolean removeConfig(String path);

    void clear();

    Set<String> updateConfig(String path, Configuration config);

    Set<String> updateConfigs(List<Configuration> configs);

    Set<String> refreshStorage(List<Configuration> actualConfigs);

    Set<String> refreshStorage(List<Configuration> actualConfigs, String tenant);

    Set<String> refreshStorage(List<Configuration> actualConfigs, Set<String> oldKeys);
}
