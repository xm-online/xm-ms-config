package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree.TenantAlias;
import com.icthh.xm.ms.configuration.repository.impl.ConfigState.IntermediateConfigState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toList;

@Slf4j
@Component
public class TenantAliasService {

    public static final String TENANT_ALIAS_CONFIG = "/config/tenants/tenant-aliases.yml";
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final ConfigurationService configurationService;
    private final TenantContextHolder tenantContextHolder;

    @Getter
    private volatile TenantAliasTree tenantAliasTree = new TenantAliasTree();

    public TenantAliasService(@Lazy ConfigurationService configurationService,
                              @Lazy TenantContextHolder tenantContextHolder) {
        this.configurationService = configurationService;
        this.tenantContextHolder = tenantContextHolder;
    }

    public List<Configuration> updateAliasTree(Configuration tenantAliasConfig) {
        try {
            TenantAliasTree tenantAliasTree = mapper.readValue(tenantAliasConfig.getContent(), TenantAliasTree.class);
            tenantAliasTree.init();

            TenantAliasTree oldTenantAliasTree = this.tenantAliasTree;
            // safe publication
            this.tenantAliasTree = tenantAliasTree;

            var oldTenants = oldTenantAliasTree.getTenants();
            var newTenants = tenantAliasTree.getTenants();
            var allTenants = new HashSet<String>();
            allTenants.addAll(oldTenants.keySet());
            allTenants.addAll(newTenants.keySet());

            List<String> tenants = allTenants.stream()
                .filter(it -> isTenantChanged(oldTenants, newTenants, it))
                .collect(toList());
            configurationService.refreshTenantsConfigurations(tenants);

        } catch (IOException e) {
            log.error("Error parse tenant alias config", e);
        }
        return Collections.emptyList();
    }

    public void setParent(String parentTenantKey) {
        String tenantKey = getRequiredTenantKeyValue(tenantContextHolder);
        TenantAliasTree.TenantAlias foundParentTenant = findTenantAlias(parentTenantKey, tenantAliasTree.getTenantAliasTree());
        TenantAliasTree.TenantAlias foundChildTenant = findTenantAlias(tenantKey, tenantAliasTree.getTenantAliasTree());
        if (foundChildTenant == null) {
            foundChildTenant = new TenantAliasTree.TenantAlias();
            foundChildTenant.setKey(tenantKey.toUpperCase());
        }

        if (foundParentTenant != null) {
            if (!containsKeyInChildren(foundParentTenant, foundChildTenant.getKey()) && !containsKeyInChildren(foundChildTenant, foundParentTenant.getKey())) {
                List<TenantAliasTree.TenantAlias> children = new ArrayList<>(requireNonNullElse(foundParentTenant.getChildren(), emptyList()));
                removeChildFromParent(foundChildTenant);
                foundChildTenant.setParent(foundParentTenant);
                children.add(foundChildTenant);
                foundParentTenant.setChildren(children);
            } else {
                log.error("Parent {} and child {} can not be on the same branch", foundParentTenant.getKey(), foundChildTenant.getKey());
                throw new TenantAliasTree.WrongTenantAliasConfiguration("Parent " + foundParentTenant.getKey() + " and child " + foundChildTenant.getKey() + " can not be on the same branch");
            }
        } else {
            List<TenantAliasTree.TenantAlias> tenantAliasTreeUpdated = new ArrayList<>(tenantAliasTree.getTenantAliasTree());
            TenantAliasTree.TenantAlias parentAlias = new TenantAliasTree.TenantAlias();
            parentAlias.setKey(parentTenantKey);
            parentAlias.setChildren(List.of(foundChildTenant));
            foundChildTenant.setParent(parentAlias);
            tenantAliasTreeUpdated.add(parentAlias);
            tenantAliasTree.setTenantAliasTree(tenantAliasTreeUpdated);
        }

        saveTenantAliases(tenantAliasTree);
    }

    private TenantAliasTree.TenantAlias findTenantAlias(String tenantKey, List<TenantAliasTree.TenantAlias> tenantAliasTree) {
        for(TenantAliasTree.TenantAlias tenantAlias: tenantAliasTree) {
            if (Objects.equals(tenantAlias.getKey(), tenantKey)) {
                return tenantAlias;
            }
            List<TenantAliasTree.TenantAlias> childrenList = requireNonNullElse(tenantAlias.getChildren(), emptyList());
            TenantAliasTree.TenantAlias result = findTenantAlias(tenantKey, childrenList);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private void removeChildFromParent(TenantAliasTree.TenantAlias childTenant) {
        TenantAliasTree.TenantAlias parentTenant = childTenant.getParent();
        if (parentTenant != null) {
            List<TenantAliasTree.TenantAlias> children = new ArrayList<>(requireNonNullElse(parentTenant.getChildren(), emptyList()));
            children.remove(childTenant);
            parentTenant.setChildren(children);
        }
    }

    private Boolean containsKeyInChildren(TenantAliasTree.TenantAlias tenant, String key) {
        List<TenantAliasTree.TenantAlias> children = requireNonNullElse(tenant.getChildren(), emptyList());
        return children.stream().anyMatch(it -> it.getKey().contains(key));
    }

    @SneakyThrows
    private void saveTenantAliases(TenantAliasTree tenantAliases) {
        String tenantAliasesYaml = mapper.writeValueAsString(tenantAliases);
        configurationService.updateConfiguration(new Configuration(TENANT_ALIAS_CONFIG, tenantAliasesYaml));
    }

    private boolean isTenantChanged(Map<String, TenantAlias> oldTenants, Map<String, TenantAlias> newTenants, String tenantKey) {
        var oldParentKey = getParentKey(oldTenants.get(tenantKey));
        var newParentKey = getParentKey(newTenants.get(tenantKey));
        return !oldParentKey.equals(newParentKey);
    }

    private Optional<String> getParentKey(TenantAlias alias) {
        return Optional.ofNullable(alias)
                .map(TenantAlias::getParent)
                .map(TenantAlias::getKey);
    }

    public void updateAliasIfChanged(IntermediateConfigState intermediateConfigState) {
        Optional.ofNullable(intermediateConfigState)
            .map(IntermediateConfigState::getChangedFiles)
            .map(it -> it.get(TENANT_ALIAS_CONFIG))
            .ifPresent(this::updateAliasTree);
    }
}
