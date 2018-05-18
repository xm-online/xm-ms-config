package com.icthh.xm.ms.configuration.service.processors;

import static java.util.stream.Collectors.toList;

import com.icthh.xm.ms.configuration.domain.Configuration;

import java.util.List;

public interface ConfigurationProcessor {

    default List<Configuration> processConfigurations(List<Configuration> configurations) {
        if (configurations == null) {
            return null;
        }

        return configurations.stream().map(this::processConfiguration).collect(toList());
    }

    Configuration processConfiguration(Configuration configuration);

}
