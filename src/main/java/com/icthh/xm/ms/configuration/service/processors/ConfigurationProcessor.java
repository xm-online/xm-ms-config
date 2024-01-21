package com.icthh.xm.ms.configuration.service.processors;

import com.icthh.xm.commons.config.domain.Configuration;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ConfigurationProcessor {

    boolean isSupported(Configuration configuration);

    List<Configuration> processConfiguration(Configuration configuration,
                                             Map<String, Configuration> originalStorage,
                                             Map<String, Configuration> targetStorage,
                                             Set<Configuration> configToReprocess);

}
