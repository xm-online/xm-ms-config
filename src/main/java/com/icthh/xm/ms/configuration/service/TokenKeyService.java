package com.icthh.xm.ms.configuration.service;

import static java.lang.System.getenv;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.ms.configuration.config.Constants;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class TokenKeyService {

    private static final String ENV_VAR_KEY = "PUBLIC_CER";

    private final Map<String, String> env = getenv();
    private ConfigurationService configurationService;

    public TokenKeyService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @LoggingAspectConfig(resultDetails = false)
    public String getKey() {
        if (env.get(ENV_VAR_KEY) != null) {
            return env.get(ENV_VAR_KEY);
        }

        Optional<Configuration> config = configurationService
                .findConfiguration(Constants.CONFIG + Constants.PUBLIC_KEY_FILE);
        if (!config.isPresent()) {
            return null;
        }
        Configuration configuration = config.get();

        return configuration.getContent();
    }

}
