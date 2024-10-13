package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.config.BeanConfiguration.TENANT_CONFIGURATION_LOCK;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.filterByTenant;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getPathInTenant;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getPathsByTenants;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenants;
import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree;
import com.icthh.xm.ms.configuration.repository.impl.ConfigState.IntermediateConfigState;
import com.icthh.xm.ms.configuration.service.TenantAliasService;
import com.icthh.xm.ms.configuration.service.processors.TenantConfigurationProcessor;
import com.icthh.xm.ms.configuration.utils.LockUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

@Slf4j
public class MemoryConfigStorageImpl implements MemoryConfigStorage {

    // same for /commons folder and for root config, don't change value
    public static final String COMMONS_CONFIG = "commons";

    private final Map<String, ConfigState> configurations = new ConcurrentHashMap<>();

    private final List<TenantConfigurationProcessor> configurationProcessors;
    private final TenantAliasService tenantAliasService;
    private final ApplicationProperties applicationProperties;
    private final Lock lock;

    public MemoryConfigStorageImpl(List<TenantConfigurationProcessor> configurationProcessors,
                                   TenantAliasService tenantAliasService,
                                   ApplicationProperties applicationProperties,
                                   @Qualifier(TENANT_CONFIGURATION_LOCK)
                                   Lock lock) {
        this.applicationProperties = applicationProperties;
        AnnotationAwareOrderComparator.sort(configurationProcessors);
        this.configurationProcessors = configurationProcessors;
        this.tenantAliasService = tenantAliasService;
        this.lock = lock;
    }

    @Override
    public Map<String, Configuration> getProcessedConfigs() {
        return configurations.values().stream()
            .map(ConfigState::getProcessedConfiguration)
            .flatMap(map -> map.values().stream())
            .collect(toMap(Configuration::getPath, identity(), mergeOverride()));
    }

    @Override
    public Map<String, Configuration> getProcessedConfigs(Collection<String> paths) {
        return getByPaths(paths, ConfigState::getProcessedConfiguration)
            .stream()
            .collect(toMap(Configuration::getPath, identity(), mergeOverride()));
    }

    @Override
    public Optional<Configuration> getProcessedConfig(String path) {
        List<Configuration> configs = getByPaths(List.of(path), ConfigState::getProcessedConfiguration);
        return configs.stream().findFirst();
    }

    @Override
    public Optional<Configuration> getConfig(String path) {
        List<Configuration> configs = getByPaths(List.of(path), ConfigState::getInmemoryConfigurations);
        return configs.stream().findFirst();
    }

    @Override
    public List<Configuration> getConfigsFromTenant(String tenant) {
        Collection<Configuration> values = configurations.get(tenant).getInmemoryConfigurations().values();
        return List.copyOf(values);
    }

    @Override
    public List<Configuration> getConfigs(Collection<String> path) {
        return getByPaths(path, ConfigState::getInmemoryConfigurations);
    }

    @Override
    public Set<String> remove(Collection<String> paths) {
        var configs = paths.stream().map(it -> new Configuration(it, "")).collect(toList());
        return saveConfigs(configs);
    }

    @Override
    public Set<String> replaceByConfiguration(List<Configuration> actualConfigs) {
        Map<String, Map<String, Configuration>> configsByTenants = getTenants(actualConfigs);
        configsByTenants.forEach((tenant, configs) -> {
            addDeletedConfiguration(actualConfigs, tenant);
        });
        return saveConfigs(actualConfigs);
    }

    @Override
    public Set<String> replaceByConfigurationInTenant(List<Configuration> actualConfigs, String tenant) {
        actualConfigs = filterByTenant(actualConfigs, tenant);
        addDeletedConfiguration(actualConfigs, tenant);
        return saveConfigs(actualConfigs);
    }

    @Override
    public Set<String> replaceByConfigurationInTenants(List<Configuration> actualConfigs, List<String> tenants) {
        tenants.forEach(tenant -> addDeletedConfiguration(actualConfigs, tenant));
        return saveConfigs(actualConfigs);
    }

    /**
     * Save configurations to memory storage.
     * @param configs configurations that created/updated or removed. Configuration with empty content will be removed.
     * @return set of changed files
     */
    @Override
    public Set<String> saveConfigs(List<Configuration> configs) {
        return LockUtils.runWithLock(lock, applicationProperties.getUpdateConfigWaitTimeSecond(), () -> {
            Set<String> changedFiles = new HashSet<>(toPathsList(configs));
            Map<String, Map<String, Configuration>> configsByTenants = getTenants(configs);

            Map<String, IntermediateConfigState> forUpdate = new HashMap<>();
            List<String> tenants = new ArrayList<>(configsByTenants.keySet());
            tenants.forEach(tenant -> {
                Map<String, Configuration> updatedConfigs = configsByTenants.get(tenant);
                var updateConfigState = requestUpdate(tenant, forUpdate);
                updateConfigState.updateConfigurations(updatedConfigs);
            });

            applyTenantAlias(forUpdate);

            tenants.forEach(tenant -> {
                processTenantConfig(forUpdate.get(tenant));
            });

            forUpdate.values().forEach(state -> changedFiles.addAll(state.getChangedFiles().keySet()));

            var updatedTenants = convertMapValue(forUpdate, ConfigState::new);
            configurations.putAll(updatedTenants); // publish changes
            return changedFiles;
        });
    }

    @Override
    public void clear() {
        configurations.clear();
    }

    private IntermediateConfigState requestUpdate(String tenant, Map<String, IntermediateConfigState> forUpdate) {
        return forUpdate.computeIfAbsent(tenant, it ->
            configurations.computeIfAbsent(tenant, ConfigState::new).toIntermediateConfigState()
        );
    }

    private static <K, I, O> Map<K, O> convertMapValue(Map<K, I> forUpdate, Function<I, O> mapper) {
        return forUpdate.entrySet().stream().collect(toMap(Map.Entry::getKey, entry -> mapper.apply(entry.getValue())));
    }

    private static <O> BinaryOperator<O> mergeOverride() {
        return (a, b) -> b;
    }

    private void addDeletedConfiguration(List<Configuration> actualConfigs, String tenant) {
        var state = configurations.get(tenant);
        if (state != null) {
            var deleted = state.calculateDeleted(actualConfigs);
            actualConfigs.addAll(deleted); // add empty configs to correct reprocess and remove from memory
        }
    }

    private void processTenantConfig(IntermediateConfigState state) {
        processConfigurations(state.getChangedFiles().values(), state);
    }

    private void processConfigurations(Collection<Configuration> configurations, IntermediateConfigState state) {
        state.cleanProcessedConfiguration(toPathsList(configurations));
        Set<Configuration> configToReprocess = new HashSet<>();
        for(Configuration configuration : configurations) {
            for(var processor: configurationProcessors) {
                var configs = processor.safeRun(configuration, state, configToReprocess);
                state.addProcessedConfiguration(configuration, configs);
            }
        }

        if (!configToReprocess.isEmpty()) {
            processConfigurations(configToReprocess, state);
        }
    }

    private List<String> extendsTenantList(Collection<String> tenants) {
        TenantAliasTree tenantAliasTree = tenantAliasService.getTenantAliasTree();
        Set<String> allTenants = new HashSet<>(tenants);
        tenants.forEach(tenant -> {
            allTenants.addAll(tenantAliasTree.getChildrenKeys(tenant));
        });
        return new ArrayList<>(allTenants);
    }

    private void applyTenantAlias(Map<String, IntermediateConfigState> forUpdate) {
        List<String> tenants = extendsTenantList(forUpdate.keySet());
        var tenantAliasTree = tenantAliasService.getTenantAliasTree();
        tenants.forEach(tenant -> {
            var state = requestUpdate(tenant, forUpdate);
            Optional<String> parentTenantKey = tenantAliasTree.getParent(tenant);
            parentTenantKey.ifPresent(parentTenant -> {
                IntermediateConfigState parent = forUpdate.get(parentTenant);
                Map<String, Configuration> parentConfigs = parent == null ? Map.of() : parent.getChangedFiles();
                Map<String, Configuration> childConfigs = toChildConfigs(tenant, parentConfigs);
                state.extendInMemoryConfigurations(childConfigs);
            });
        });
    }

    private Map<String, Configuration> toChildConfigs(String tenant, Map<String, Configuration> updatedConfigs) {
        Map<String, Configuration> result = new HashMap<>();
        updatedConfigs.forEach((path, config) -> {
            String tenantPath = getPathInTenant(path, tenant);
            result.put(tenantPath, config);
        });
        return result;
    }

    private static List<String> toPathsList(Collection<Configuration> configs) {
        return configs.stream().map(Configuration::getPath).collect(toList());
    }

    private List<Configuration> getByPaths(Collection<String> paths, Function<ConfigState, Map<String, Configuration>> configType) {
        var tenants = getPathsByTenants(paths);
        List<Configuration> result = new ArrayList<>();
        tenants.forEach((tenant, configs) -> {
            ConfigState state = configurations.get(tenant);
            Map<String, Configuration> tenantConfigs = state == null? emptyMap() : configType.apply(state);
            configs.stream()
                .map(tenantConfigs::get)
                .filter(Objects::nonNull)
                .forEach(result::add);
        });
        return result;
    }

}
