package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PersistenceConfigRepository {

    boolean hasVersion(ConfigVersion version);

    ConfigurationList findAll();

    ConfigurationList findAllInTenant(String tenantKey);

    ConfigurationItem find(String path);

    Configuration find(String path, ConfigVersion commit);

    default ConfigVersion save(Configuration configuration) {
        return saveAll(List.of(configuration), Map.of());
    }

    ConfigVersion saveAll(List<Configuration> configurations, Map<String, String> configHashes);

    ConfigVersion setRepositoryState(List<Configuration> configurations);

    ConfigVersion deleteAll(List<String> paths);

    void recloneConfiguration();

    ConfigVersion getCurrentVersion();

    ConfigurationList findAllInTenants(Set<String> folders);
}
