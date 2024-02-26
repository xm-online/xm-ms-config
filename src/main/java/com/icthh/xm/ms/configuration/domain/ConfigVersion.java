package com.icthh.xm.ms.configuration.domain;

import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.icthh.xm.ms.configuration.repository.impl.JGitRepository.UNDEFINED_COMMIT;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@ToString
public class ConfigVersion {

    public static final ConfigVersion UNDEFINED_VERSION = new ConfigVersion();

    @Getter
    private final String mainVersion;
    @Getter
    private final Map<String, ConfigVersion> externalTenantVersions;

    private ConfigVersion() {
        this(UNDEFINED_COMMIT);
    }

    public ConfigVersion(String instanceVersion) {
        this(instanceVersion, emptyMap());
    }

    public ConfigVersion(String instanceVersion, Map<String, ConfigVersion> externalTenantVersions) {
        this.mainVersion = firstNonNull(instanceVersion, UNDEFINED_COMMIT);
        this.externalTenantVersions = firstNonNull(externalTenantVersions, emptyMap());
    }

    public ConfigVersion mainVersion(ConfigVersion instanceVersion) {
        return new ConfigVersion(instanceVersion.getMainVersion(), externalTenantVersions);
    }

    public ConfigVersion addExternalTenantVersion(String tenant, ConfigVersion version) {
        Map<String, ConfigVersion> newExternalTenantVersions = new HashMap<>(externalTenantVersions);
        newExternalTenantVersions.put(tenant, version);
        return new ConfigVersion(mainVersion, unmodifiableMap(newExternalTenantVersions));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigVersion that = (ConfigVersion) o;
        return Objects.equals(mainVersion, that.mainVersion) && Objects.equals(externalTenantVersions, that.externalTenantVersions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mainVersion, externalTenantVersions);
    }

}
