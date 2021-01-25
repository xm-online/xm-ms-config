package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree;
import com.icthh.xm.ms.configuration.service.processors.PublicConfigurationProcessor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Listen change of tenantAliasTree. Using PublicConfigurationProcessor because using RefreshableConfiguration cause
 * unresolvable cyclic dependency.
 */
@Slf4j
@Service
public class TenantAliasService implements PublicConfigurationProcessor {

    private static final String TENANT_ALIAS_CONFIG = "/config/tenants/tenant-aliases.yml";
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final ConfigurationService configurationService;

    @Getter
    private volatile TenantAliasTree tenantAliasTree = new TenantAliasTree();

    public TenantAliasService(@Lazy ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @Override
    public boolean isSupported(Configuration configuration) {
        return TENANT_ALIAS_CONFIG.equals(configuration.getPath());
    }

    @Override
    public List<Configuration> processConfiguration(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage) {
        try {
            TenantAliasTree tenantAliasTree = mapper.readValue(configuration.getContent(), TenantAliasTree.class);
            tenantAliasTree.init();
            // safe publication
            this.tenantAliasTree = tenantAliasTree;
            tenantAliasTree.getTenants().keySet().forEach(configurationService::refreshTenantConfigurations);
        } catch (IOException e) {
            log.error("Error parse tenant alias config", e);
        }
        return Collections.emptyList();
    }
}
