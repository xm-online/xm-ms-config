package com.icthh.xm.ms.configuration.service.processors;

import static java.util.stream.Collectors.toList;

import com.icthh.xm.commons.config.domain.Configuration;

import java.util.Collections;
import java.util.List;

public interface ConfigurationProcessor {

    default boolean isPrivate() {
        return false;
    }

    default List<Configuration> processToConfigurations(Configuration configuration) {
        return Collections.singletonList(processToConfiguration(configuration));
    }

    Configuration processToConfiguration(Configuration configuration);

}
