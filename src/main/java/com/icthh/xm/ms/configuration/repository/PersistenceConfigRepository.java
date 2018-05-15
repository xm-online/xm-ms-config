package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.domain.Configuration;

import java.util.Collection;
import java.util.List;

public interface PersistenceConfigRepository {

    List<Configuration> findAll();

    Configuration find(String path);

    String saveAll(List<Configuration> configurations);

    String save(Configuration configuration);

    String save(Configuration configuration, String oldConfigHash);

    String deleteAll(List<String> paths);

    String delete(String path);
}
