package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.Configurations;

import java.util.List;

public interface PersistenceConfigRepository {

    Configurations findAll();

    Configuration find(String path);

    String saveAll(List<Configuration> configurations);

    String save(Configuration configuration);

    String save(Configuration configuration, String oldConfigHash);

    String deleteAll(List<String> paths);

    String delete(String path);
}
