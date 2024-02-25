package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;

import java.util.List;
import java.util.Map;

public interface DistributedConfigRepository extends PersistenceConfigRepository {

    Map<String, Configuration> getMap(ConfigVersion commit);

    void updateConfigurationInMemory(Configuration configuration, ConfigVersion commit);

    void updateConfigurationsInMemory(List<Configuration> configurations, ConfigVersion commit);

    void deleteAllInMemory(List<String> paths);

    void refreshInternal();

    void refreshAll();

    void refreshAll(List<String> excludeNotificationPaths);

    void refreshPath(String path);

    void refreshTenant(String tenant);

}
