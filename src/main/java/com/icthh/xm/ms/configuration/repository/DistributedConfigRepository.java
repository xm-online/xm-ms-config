package com.icthh.xm.ms.configuration.repository;

import com.icthh.xm.commons.config.domain.Configuration;

import java.util.Map;

public interface DistributedConfigRepository extends PersistenceConfigRepository {

    Map<String, Configuration> getMap(String commit);

    void refreshAll();

    void refreshPath(String path);

    void refreshTenant(String tenant);
}
