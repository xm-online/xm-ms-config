package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.config.domain.TenantAliasTree;
import com.icthh.xm.ms.configuration.domain.UpdateAliasTreeEvent;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TenantAliasTreeService {

    public static final String TENANT_ALIAS_CONFIG = "/config/tenants/tenant-aliases.yml";
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final ConfigurationService configurationService;
    private final TenantAliasTreeStorage tenantAliasTreeStorage;

    public TenantAliasTreeService(ConfigurationService configurationService,
                                  TenantAliasTreeStorage tenantAliasTreeStorage) {
        this.configurationService = configurationService;
        this.tenantAliasTreeStorage = tenantAliasTreeStorage;
    }

    public List<Configuration> updateAliasTree(Configuration tenantAliasConfig) {
        List<String> tenants = tenantAliasTreeStorage.updateAliasTree(tenantAliasConfig);
        configurationService.refreshTenantsConfigurations(tenants);
        return Collections.emptyList();
    }

    @EventListener
    public void handleCustomEvent(UpdateAliasTreeEvent event) {
        tenantAliasTreeStorage.internalUpdateAlisTreeWithoutRefresh(event.getConfiguration());
    }

    public void setParent(String parentTenantKey) {
        tenantAliasTreeStorage.setParent(parentTenantKey);
        var tenantAliasTree = tenantAliasTreeStorage.getTenantAliasTree();
        saveTenantAliases(tenantAliasTree);
    }

    @SneakyThrows
    private void saveTenantAliases(TenantAliasTree tenantAliases) {
        String tenantAliasesYaml = mapper.writeValueAsString(tenantAliases);
        configurationService.updateConfiguration(new Configuration(TENANT_ALIAS_CONFIG, tenantAliasesYaml));
    }

}
