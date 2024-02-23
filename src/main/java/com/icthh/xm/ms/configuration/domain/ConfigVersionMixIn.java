package com.icthh.xm.ms.configuration.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public abstract class ConfigVersionMixIn {
    @JsonCreator
    public ConfigVersionMixIn(@JsonProperty("instanceVersion") String instanceVersion,
                              @JsonProperty("externalTenantVersions") Map<String, String> externalTenantVersions) {
    }
}
