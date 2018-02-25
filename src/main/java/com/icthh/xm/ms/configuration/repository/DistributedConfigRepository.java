package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.ms.configuration.domain.Configuration;

import java.util.List;

public interface DistributedConfigRepository {
    Configuration find(String path);

    void save(Configuration configuration);

    void saveAll(List<Configuration> configurations);

    void delete(String path);

    List<String> getKeysList();
}
