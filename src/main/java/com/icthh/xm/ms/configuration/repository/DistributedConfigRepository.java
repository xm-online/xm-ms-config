package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.domain.Configuration;

import java.util.List;
import java.util.Map;

public interface DistributedConfigRepository extends PersistenceConfigRepository {

    Map<String, Configuration> getMap(String commit);

    void updateConfigurationInMemory(Configuration configuration, String commit);

    void updateConfigurationsInMemory(List<Configuration> configurations, String commit);

    void deleteAllInMemory(List<String> paths);

    void refreshInternal();

    void refreshAll();

    void refreshPath(String path);

    void refreshTenant(String tenant);

    String getCommitVersion();
}
