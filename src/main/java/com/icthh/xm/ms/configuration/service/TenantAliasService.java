package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree.TenantAlias;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.service.processors.PublicConfigurationProcessor;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Listen change of tenantAliasTree. Using PublicConfigurationProcessor because using RefreshableConfiguration cause
 * unresolvable cyclic dependency.
 */
@Slf4j
@Component
public class TenantAliasService implements PublicConfigurationProcessor {

    public static final String TENANT_ALIAS_CONFIG = "/config/tenants/tenant-aliases.yml";
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final ConfigurationService configurationService;
    private final MemoryConfigStorage memoryConfigStorage;

    @Getter
    private volatile TenantAliasTree tenantAliasTree = new TenantAliasTree();

    public TenantAliasService(@Lazy ConfigurationService configurationService,
                              @Lazy MemoryConfigStorage memoryConfigStorage) {
        this.configurationService = configurationService;
        this.memoryConfigStorage = memoryConfigStorage;
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

            TenantAliasTree oldTenantAliasTree = this.tenantAliasTree;
            // safe publication
            this.tenantAliasTree = tenantAliasTree;

            var oldTenants = oldTenantAliasTree.getTenants();
            var newTenants = tenantAliasTree.getTenants();
            var allTenants = new HashSet<String>();
            allTenants.addAll(oldTenants.keySet());
            allTenants.addAll(newTenants.keySet());

            allTenants.stream().filter(it -> isTenantChanged(oldTenants, newTenants, it))
                    .map(newTenants::get)
                    .map(this::getParentKey)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .distinct()
                    .forEach(memoryConfigStorage::reprocess);

            allTenants.stream().filter(it -> isTenantChanged(oldTenants, newTenants, it))
                    .distinct()
                    .forEach(configurationService::refreshTenantConfigurations);

        } catch (IOException e) {
            log.error("Error parse tenant alias config", e);
        }
        return Collections.emptyList();
    }

    private boolean isTenantChanged(Map<String, TenantAlias> oldTenants, Map<String, TenantAlias> newTenants, String it) {
        var oldParentKey = getParentKey(oldTenants.get(it));
        var newParentKey = getParentKey(newTenants.get(it));
        return !oldParentKey.equals(newParentKey);
    }

    private Optional<String> getParentKey(TenantAlias alias) {
        return Optional.ofNullable(alias)
                .map(TenantAlias::getParent)
                .map(TenantAlias::getKey);
    }
}
