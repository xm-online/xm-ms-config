package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.config.BeanConfiguration.TENANT_CONFIGURATION_LOCK;
import static com.icthh.xm.ms.configuration.utils.LockUtils.runWithLock;
import static java.util.Collections.emptySet;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.domain.TenantState;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TenantService {

    private static final String CONFIG_LIST_STORAGE = "/config/tenants/tenants-list.json";

    private final ObjectMapper om = new ObjectMapper();

    private final ConfigurationService configurationService;

    private final Lock lock;

    private final GitProperties gitProperties;

    public TenantService(ConfigurationService configurationService,
                         @Qualifier(TENANT_CONFIGURATION_LOCK) Lock lock,
                         ApplicationProperties applicationProperties) {
        this.configurationService = configurationService;
        this.lock = lock;
        this.gitProperties = applicationProperties.getGit();
    }

    public void addTenant(String serviceName, String tenantKey) {
        runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            Map<String, Set<TenantState>> tenantsList = getTenantsList();

            tenantsList.putIfAbsent(serviceName, new HashSet<>());
            tenantsList.get(serviceName).add(new TenantState(tenantKey, "ACTIVE"));

            saveTenantList(tenantsList);
        });
    }

    public void updateTenant(String serviceName, TenantState tenant) {
        runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            Map<String, Set<TenantState>> tenantsList = getTenantsList();

            tenantsList.getOrDefault(serviceName, new HashSet<>()).remove(tenant);
            tenantsList.getOrDefault(serviceName, new HashSet<>()).add(tenant);

            saveTenantList(tenantsList);
        });
    }

    public void deleteTenant(String serviceName, String tenantKey) {
        runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            Map<String, Set<TenantState>> tenantsList = getTenantsList();

            tenantsList.getOrDefault(serviceName, new HashSet<>()).remove(new TenantState(tenantKey, "SUSPENDED"));

            saveTenantList(tenantsList);
        });
    }

    @SneakyThrows
    private void saveTenantList(Map<String, Set<TenantState>> tenantsList) {
        String tenantsListJson = om.writerWithDefaultPrettyPrinter().writeValueAsString(tenantsList);
        configurationService.updateConfiguration(new Configuration(CONFIG_LIST_STORAGE, tenantsListJson));
    }

    @SneakyThrows
    private Map<String, Set<TenantState>> getTenantsList() {
        Optional<Configuration> maybeConfiguration = configurationService.findConfiguration(CONFIG_LIST_STORAGE);
        if (!maybeConfiguration.isPresent()) {
            return new HashMap<>();
        }
        Configuration configuration = maybeConfiguration.get();

        JavaType setType = om.getTypeFactory().constructCollectionType(Set.class, TenantState.class);
        JavaType stringType = om.getTypeFactory().constructType(String.class);
        JavaType mapType = om.getTypeFactory().constructMapType(Map.class, stringType, setType);

        return om.readValue(configuration.getContent(), mapType);
    }

    public Set<TenantState> getTenants(String serviceName) {
        return getTenantsList().getOrDefault(serviceName, emptySet());
    }

    public Set<String> getServices(String tenantKey) {
        Map<String, Set<TenantState>> tenantsList = getTenantsList();
        return tenantsList.entrySet().stream()
                    .filter(it -> containsTenant(it.getValue(), tenantKey))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
    }

    private Boolean containsTenant(Set<TenantState> tenants, String tenantKey) {
        return tenants.stream().anyMatch(it -> it.getName().equals(tenantKey));
    }
}
