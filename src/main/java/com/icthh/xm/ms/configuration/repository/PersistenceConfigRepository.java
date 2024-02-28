package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;

import java.util.List;

public interface PersistenceConfigRepository {

    boolean hasVersion(ConfigVersion version);

    ConfigurationList findAll();

    ConfigurationItem find(String path);

    Configuration find(String path, ConfigVersion commit);

    ConfigVersion saveAll(List<Configuration> configurations);

    ConfigVersion setRepositoryState(List<Configuration> configurations);

    ConfigVersion save(Configuration configuration);

    ConfigVersion save(Configuration configuration, String oldConfigHash);

    ConfigVersion deleteAll(List<String> paths);

    ConfigVersion delete(String path);

    ConfigVersion saveOrDeleteEmpty(List<Configuration> configurations);

    void recloneConfiguration();

    ConfigVersion getCurrentVersion();
}
