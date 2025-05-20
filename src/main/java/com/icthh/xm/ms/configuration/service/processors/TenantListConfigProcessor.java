package com.icthh.xm.ms.configuration.service.processors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.config.domain.TenantState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.icthh.xm.commons.config.client.repository.TenantListRepository.TENANTS_LIST_CONFIG_KEY;
import static java.util.Collections.emptyList;

@Slf4j
@Component
public class TenantListConfigProcessor implements TenantConfigurationProcessor {

    private final ObjectMapper mapper = new ObjectMapper();
    private final String applicationName;
    public final Map<String, List<TenantState>> tenantListStub;

    public TenantListConfigProcessor(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
        this.tenantListStub = Map.of(applicationName, List.of(new TenantState("XM", "ACTIVE")));
    }

    @Override
    public boolean isSupported(Configuration configuration) {
        return TENANTS_LIST_CONFIG_KEY.equals(configuration.getPath());
    }

    @Override
    public List<Configuration> processConfiguration(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage,
                                                    Set<Configuration> configToReprocess,
                                                    Map<String, Set<Configuration>> externalConfigs) {
        try {
            Map<String, List<TenantState>> appToTenantsMap = mapper.readValue(configuration.getContent(), new TypeReference<>() {
            });
            List<TenantState> tenants = appToTenantsMap.getOrDefault(applicationName, List.of());
            if (tenants.isEmpty()) {
                appToTenantsMap.putAll(tenantListStub);
            }
            return List.of(new Configuration(configuration.getPath(), mapper.writeValueAsString(appToTenantsMap)));
        } catch (Exception e) {
            log.error("Error parse tenant-list.json", e);
            return emptyList();
        }

    }

}
