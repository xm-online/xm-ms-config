package com.icthh.xm.ms.configuration.service.processors;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.repository.impl.ConfigState.IntermediateConfigState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface TenantConfigurationProcessor {

    Logger log = LoggerFactory.getLogger(TenantConfigurationProcessor.class);
    Integer DEFAULT_PRIORITY = 100;

    default Integer getPriority() {
        return DEFAULT_PRIORITY;
    }

    boolean isSupported(Configuration configuration);

    List<Configuration> processConfiguration(Configuration configuration,
                                             Map<String, Configuration> originalStorage,
                                             Map<String, Configuration> targetStorage,
                                             Set<Configuration> configToReprocess,
                                             Map<String, Set<Configuration>> externalConfigs);

    default Map<String, Configuration> safeRun(Configuration configuration,
                                               IntermediateConfigState state,
                                               Set<Configuration> configToReprocess,
                                               Map<String, Set<Configuration>> external) {
        try {
            if(configuration == null || isBlank(configuration.getContent()) || !isSupported(configuration)) {
                return Map.of();
            }

            var processed = state.getProcessedConfiguration();
            var inMemory = state.getInmemoryConfigurations();
            var configurations = processConfiguration(configuration, inMemory, processed, configToReprocess, external);
            return configurations.stream().collect(toMap(Configuration::getPath, identity()));
        } catch (Exception e) {
            log.error("Error run processor", e);
        }
        return Map.of();
    }
}
