package com.icthh.xm.ms.configuration.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import lombok.Data;

import java.util.Map;

import static java.util.Collections.emptyMap;

@Data
public class ExternalTenantsConfig {
    @JsonProperty("external-tenants")
    private Map<String, GitProperties> externalTenants = emptyMap();

    public Map<String, GitProperties> getExternalTenants() {
        return externalTenants == null ? emptyMap() : externalTenants;
    }
}
