package com.icthh.xm.ms.configuration.service.dto;

import com.icthh.xm.commons.config.domain.Configuration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.icthh.xm.ms.configuration.config.Constants.EXTERNAL_TOKEN;
import static com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorageImpl.COMMONS_CONFIG;

@Getter
@Setter
@AllArgsConstructor
public class FullConfigurationDto {

    private Map<String, Map<String, Configuration>> tenantsConfigs;
    private Map<String, Set<Configuration>> externalConfigs;
    private Set<String> changedFiles;

    public FullConfigurationDto() {
        this.tenantsConfigs = new HashMap<>();
        this.externalConfigs = new ConcurrentHashMap<>();
        this.changedFiles = new HashSet<>();
    }

    public void computeExternalIfAbsent(Configuration configuration) {
        this.externalConfigs.computeIfAbsent(EXTERNAL_TOKEN, k -> new HashSet<>()).add(configuration);
    }

    public void computeTenantIfAbsent(String key, Configuration configuration) {
        if (StringUtils.isEmpty(key)) {
            key = COMMONS_CONFIG;
        }
        this.tenantsConfigs.computeIfAbsent(key, k -> new HashMap<>()).put(configuration.getPath(), configuration);
    }

    public void addChangedFiles(String path) {
        this.changedFiles.add(path);
    }
}
