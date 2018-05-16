package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;

import java.util.List;

public interface PersistenceConfigRepository {

    boolean hasVersion(String version);

    ConfigurationList findAll();

    ConfigurationItem find(String path);

    String saveAll(List<Configuration> configurations);

    String save(Configuration configuration);

    String save(Configuration configuration, String oldConfigHash);

    String deleteAll(List<String> paths);

    String delete(String path);
}
