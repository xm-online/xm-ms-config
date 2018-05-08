package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.ms.configuration.config.Constants;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TokenKeyService {

    private ConfigurationService configurationService;

    public TokenKeyService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @LoggingAspectConfig(resultDetails = false)
    public String getKey() {
        Optional<Configuration> config = configurationService.findConfiguration(Constants.CONFIG + Constants.PUBLIC_KEY_FILE);
        if (!config.isPresent()) {
            return null;
        }
        Configuration configuration = config.get();

        return configuration.getContent();

    }

}
