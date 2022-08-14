package com.icthh.xm.ms.configuration.service.processors;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Boolean.TRUE;
import static java.lang.System.getenv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

@Slf4j
@Component
@Order(LOWEST_PRECEDENCE)
public class EnvConfigExternalization implements PrivateConfigurationProcessor {

    private final Map<String, String> environment;
    private final ApplicationProperties applicationProperties;

    public EnvConfigExternalization(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
        Map<String, String> env = new HashMap<>();
        getenv().forEach((key, value) -> env.put("environment." + key, value));
        this.environment = env;
    }

    @Override
    public boolean isSupported(Configuration configuration) {
        return true;
    }

    @Override
    @SneakyThrows
    public List<Configuration> processConfiguration(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage) {
        if (TRUE.equals(applicationProperties.getEnvConfigExternalizationEnabled())) {
            String content = StrSubstitutor.replace(configuration.getContent(), environment);
            return singletonList(new Configuration(configuration.getPath(), content));
        }
        return emptyList();
    }

}
