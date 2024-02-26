package com.icthh.xm.ms.configuration.domain;

import com.icthh.xm.commons.config.domain.Configuration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class ConfigurationItem {

    private final ConfigVersion version;
    private final Configuration data;

    public ConfigurationItem(ConfigVersion version, Configuration data) {
        this.version = version;
        this.data = data;
    }
}
