package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.domain.Configuration;

import java.util.List;

public interface PersistenceConfigRepository {

    List<Configuration> findAll();

    Configuration find(String path);

    void saveAll(List<Configuration> configurations);

    void save(Configuration configuration);

    void save(Configuration configuration, String oldConfigHash);

    void delete(String path);
}
