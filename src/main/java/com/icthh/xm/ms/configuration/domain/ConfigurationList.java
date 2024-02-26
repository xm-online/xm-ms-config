package com.icthh.xm.ms.configuration.domain;

import com.icthh.xm.commons.config.domain.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
public class ConfigurationList {

    private ConfigVersion version;
    private final List<Configuration> data;

    public ConfigurationList(ConfigVersion version, List<Configuration> data) {
        this.version = version;
        this.data = data;
    }

    public void setMainVersion(ConfigVersion version) {
        this.version = this.version.mainVersion(version);
    }

    public void addExternalTenantVersion(String tenantKey, ConfigVersion version) {
        this.version = this.version.addExternalTenantVersion(tenantKey, version);
    }
}
