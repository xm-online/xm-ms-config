package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.config.BeanConfiguration.TENANT_CONFIGURATION_LOCK;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.addAllByKey;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.filterByTenant;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getPathInTenant;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getPathsByTenants;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantsByPaths;
import static java.util.Collections.emptyMap;
import static java.util.Comparator.comparingInt;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.config.domain.TenantAliasTree;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.repository.impl.ConfigState.IntermediateConfigState;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeStorage;
import com.icthh.xm.ms.configuration.service.dto.FullConfigurationDto;
import com.icthh.xm.ms.configuration.service.processors.TenantConfigurationProcessor;
import com.icthh.xm.ms.configuration.utils.ConfigPathUtils;
import com.icthh.xm.ms.configuration.utils.LockUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.AntPathMatcher;

@Slf4j
public class MemoryConfigStorageImpl implements MemoryConfigStorage {

    // same for /commons folder and for root config, don't change value
    public static final String COMMONS_CONFIG = "commons";

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    private final Map<String, ConfigState> tenantConfigStates = new ConcurrentHashMap<>();
    private volatile Map<String, Set<Configuration>> externalConfigs = Map.of();

    private final List<TenantConfigurationProcessor> configurationProcessors;
    private final TenantAliasTreeStorage tenantAliasTreeStorage;
    private final ApplicationProperties applicationProperties;
    private final Lock lock;

    public MemoryConfigStorageImpl(List<TenantConfigurationProcessor> configurationProcessors,
                                   TenantAliasTreeStorage tenantAliasTreeStorage,
                                   ApplicationProperties applicationProperties,
                                   @Qualifier(TENANT_CONFIGURATION_LOCK)
                                   Lock lock) {
        this.applicationProperties = applicationProperties;
        AnnotationAwareOrderComparator.sort(configurationProcessors);
        this.configurationProcessors = configurationProcessors;
        this.tenantAliasTreeStorage = tenantAliasTreeStorage;
        this.lock = lock;
    }

    @Override
    public Map<String, Configuration> getProcessedConfigs() {
        return tenantConfigStates.values().stream()
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
    public Map<String, Configuration> getProcessedConfigsByAntPatterns(Collection<String> patterns) {
        return getByAntPatternPaths(patterns)
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
        if (path.startsWith(TENANT_PREFIX)) {
            List<Configuration> configs = getByPaths(List.of(path), ConfigState::getInmemoryConfigurations);
            return configs.stream().findFirst();
        } else {
            String key = path.split("/")[2];
            return this.externalConfigs.getOrDefault(key, Set.of()).stream()
                .filter(config -> config.getPath().equals(path))
                .findFirst();
        }
    }

    @Override
    public List<Configuration> getConfigsFromTenant(String tenant) {
        return Optional.ofNullable(tenantConfigStates.get(tenant))
            .map(ConfigState::getInmemoryConfigurations)
            .map(configs -> List.copyOf(configs.values()))
            .orElse(List.of());
    }

    @Override
    public Set<String> getTenants() {
        return tenantConfigStates.keySet();
    }

    @Override
    public Optional<Set<Configuration>> getExternalConfig(String configKey) {
        return Optional.ofNullable(this.externalConfigs.get(configKey));
    }

    @Override
    public List<Configuration> getConfigs(String tenant, Collection<String> paths) {
        Map<String, Configuration> configs = Optional.ofNullable(tenantConfigStates.get(tenant))
            .map(ConfigState::getInmemoryConfigurations)
            .orElse(Map.of());

        return paths.stream().map(configs::get).filter(Objects::nonNull).collect(toList());
    }

    @Override
    public Set<String> remove(Collection<String> paths) {
        log.info("Remove configurations by paths: {}", paths);
        List<Configuration> configs = tenantConfigStates.values().stream()
            .flatMap(state -> state.pathsByFolder(paths).stream())
            .map(path -> new Configuration(path, ""))
            .collect(toList());
        return saveConfigs(configs);
    }

    @Override
    public Set<String> replaceByConfiguration(List<Configuration> actualConfigs) {
        StopWatch stopWatch = StopWatch.createStarted();
        log.info("Full configuration refresh inmemory started");

        var fullConfiguration = getFullConfiguration(actualConfigs);
        var deletedExternalConfigs = getDeletedExternalConfiguration(fullConfiguration);
        actualConfigs.addAll(deletedExternalConfigs);
        Map<String, Map<String, Configuration>> configsByTenants = fullConfiguration.getTenantsConfigs();
        configsByTenants.forEach((tenant, configs) -> {
            addDeletedConfiguration(actualConfigs, tenant);
        });
        Set<String> updated = saveConfigs(actualConfigs, true);
        log.info("Full configuration refresh inmemory finished in {} ms", stopWatch.getTime());
        return updated;
    }

    private Set<Configuration> getDeletedExternalConfiguration(FullConfigurationDto fullConfiguration) {
        Map<String, Set<Configuration>> newExternalConfigs = fullConfiguration.getExternalConfigs();
        var externalConfigs = new HashMap<>(this.externalConfigs);

        Set<Configuration> result = new HashSet<>();
        Set<String> externalConfigKeys = externalConfigs.keySet();
        for (String key: externalConfigKeys) {
            Set<Configuration> newConfigs = newExternalConfigs.getOrDefault(key, Set.of());
            Set<Configuration> oldConfigs = externalConfigs.get(key);
            oldConfigs.stream()
                .filter(c -> !newConfigs.contains(c))
                .map(it -> new Configuration(it.getPath(), ""))
                .forEach(result::add);
        }
        return result;
    }

    @Override
    public Set<String> replaceByConfigurationInTenant(List<Configuration> actualConfigs, String tenant) {
        StopWatch stopWatch = StopWatch.createStarted();
        log.info("Configuration refresh inmemory for tenant {} started", tenant);
        actualConfigs = filterByTenant(actualConfigs, tenant);
        addDeletedConfiguration(actualConfigs, tenant);
        Set<String> updated = saveConfigs(actualConfigs, true);
        log.info("Configuration refresh inmemory for tenant {} finished in {} ms", tenant, stopWatch.getTime());
        return updated;
    }

    @Override
    public Set<String> replaceByConfigurationInTenants(List<Configuration> actualConfigs, List<String> tenants) {
        tenants.forEach(tenant -> addDeletedConfiguration(actualConfigs, tenant));
        return saveConfigs(actualConfigs, true);
    }

    /**
     * Save configurations to memory storage.
     * @param configs configurations that created/updated or removed. Configuration with empty content will be removed.
     * @return set of changed files
     */
    @Override
    public Set<String> saveConfigs(List<Configuration> configs) {
        return saveConfigs(configs, false);
    }

    private Set<String> saveConfigs(List<Configuration> configs, boolean fullReload) {
        StopWatch stopWatch = StopWatch.createStarted();

        log.info("Save configurations to inmemory storage configs.size: {}", configs.size());
        var fullConfiguration = getFullConfiguration(configs);

        log.info("Try to acquire lock for update configuration");
        return LockUtils.runWithLock(lock, applicationProperties.getUpdateConfigWaitTimeSecond(), "memory storage", () -> {
            log.info("Lock acquired for update configuration after {} ms", stopWatch.getTime());

            updateExternalConfigs(fullConfiguration.getExternalConfigs());

            Set<String> changedFiles = fullConfiguration.getChangedFiles();
            Map<String, Map<String, Configuration>> configsByTenants = fullConfiguration.getTenantsConfigs();

            Map<String, IntermediateConfigState> forUpdate = new HashMap<>();
            List<String> tenants = new ArrayList<>(configsByTenants.keySet());
            tenants.forEach(tenant -> {
                Map<String, Configuration> updatedConfigs = configsByTenants.get(tenant);
                var updateConfigState = requestUpdate(tenant, forUpdate);
                updateConfigState.updateConfigurations(updatedConfigs);
            });

            // global config processing
            applyTenantAlias(forUpdate, fullReload);

            forUpdate.keySet().forEach(tenant -> {
                // tenant config processing
                processTenantConfig(forUpdate.get(tenant));
            });

            forUpdate.values().forEach(state -> changedFiles.addAll(state.getChangedFiles().keySet()));

            var updatedTenants = convertMapValue(forUpdate, ConfigState::new);
            tenantConfigStates.putAll(updatedTenants); // publish changes
            log.info("Configuration inmemory updated in {} ms", stopWatch.getTime());
            return changedFiles;
        });
    }

    private void updateExternalConfigs(Map<String, Set<Configuration>> updatedConfigs) {
        Map<String, Set<Configuration>> resultConfigs = new HashMap<>();
        externalConfigs.forEach((key, c) -> addAllByKey(resultConfigs, key, c));
        updatedConfigs.forEach((key, c) -> addAllByKey(resultConfigs, key, c));
        resultConfigs.values().forEach(configs -> configs.removeIf(config -> isBlank(config.getContent())));

        var mapWithImmutableValue = new HashMap<String, Set<Configuration>>();
        resultConfigs.forEach((key, value) -> mapWithImmutableValue.put(key, Set.copyOf(value)));
        this.externalConfigs = Map.copyOf(mapWithImmutableValue);
    }

    private FullConfigurationDto getFullConfiguration(List<Configuration> configs) {
        return ConfigPathUtils.getFullConfiguration(configs, applicationProperties.getExcludeConfigPatterns());
    }

    @Override
    public void clear() {
        tenantConfigStates.clear();
        externalConfigs = Map.of();
    }

    private IntermediateConfigState requestUpdate(String tenant, Map<String, IntermediateConfigState> forUpdate) {
        return forUpdate.computeIfAbsent(tenant, it ->
            tenantConfigStates.computeIfAbsent(tenant, ConfigState::new).toIntermediateConfigState()
        );
    }

    private static <K, I, O> Map<K, O> convertMapValue(Map<K, I> forUpdate, Function<I, O> mapper) {
        return forUpdate.entrySet().stream().collect(toMap(Entry::getKey, entry -> mapper.apply(entry.getValue())));
    }

    private static <O> BinaryOperator<O> mergeOverride() {
        return (a, b) -> b;
    }

    private void addDeletedConfiguration(List<Configuration> actualConfigs, String tenant) {
        var state = tenantConfigStates.get(tenant);
        if (state != null) {
            var deleted = state.calculateDeleted(actualConfigs);
            actualConfigs.addAll(deleted); // add empty configs to correct reprocess and remove from memory
        }
    }

    private void processTenantConfig(IntermediateConfigState state) {
        processConfigurations(state.getChangedFiles().values(), state);
    }

    private void processConfigurations(Collection<Configuration> configurations, IntermediateConfigState state) {
        var changedConfigurationFiles = new ArrayList<>(configurations); // copy list before modification in clean and in iteration
        state.cleanProcessedConfiguration(toPathsList(configurations));
        Set<Configuration> configToReprocess = new HashSet<>();
        for(Configuration configuration : changedConfigurationFiles) {
            configurationProcessors.stream()
                .sorted(Comparator.comparing(TenantConfigurationProcessor::getPriority))
                .forEach(processor -> {
                    var configs = processor.safeRun(configuration, state, configToReprocess, externalConfigs);
                    state.addProcessedConfiguration(configuration, configs);
                    addProducedFileToProcessingQueueIfExist(configuration, state, configToReprocess);
                });
        }
        if (!configToReprocess.isEmpty()) {
            processConfigurations(configToReprocess, state);
        }
    }

    private void addProducedFileToProcessingQueueIfExist(Configuration configuration,
                                                         IntermediateConfigState state,
                                                         Set<Configuration> configToReprocess) {
        Optional<String> producedByFile = state.getProducedByFile(configuration.getPath());
        if (producedByFile.isPresent() && !producedByFile.get().equals(configuration.getPath())) {
            Configuration producedConfiguration = state.getInmemoryConfigurations().get(producedByFile.get());
            configToReprocess.add(producedConfiguration);
        }
    }

    private List<String> extendsTenantList(Collection<String> tenants) {
        TenantAliasTree tenantAliasTree = tenantAliasTreeStorage.getTenantAliasTree();
        Set<String> allTenants = new HashSet<>(tenants);
        tenants.forEach(tenant -> {
            allTenants.addAll(tenantAliasTree.getAllChildrenRecursive(tenant));
        });
        List<String> tenantsList = new ArrayList<>(allTenants);
        tenantsList.sort(comparingInt(tenant -> tenantAliasTree.getParents(tenant).size()));
        return tenantsList;
    }

    private void applyTenantAlias(Map<String, IntermediateConfigState> forUpdate, boolean fullReload) {
        StopWatch stopWatch = StopWatch.createStarted();
        List<String> tenants = extendsTenantList(forUpdate.keySet());
        log.info("Start apply alias for tenants {}", tenants);
        TenantAliasTree tenantAliasTree = tenantAliasTreeStorage.getTenantAliasTree();
        tenants.forEach(tenant -> {
            var state = requestUpdate(tenant, forUpdate);
            TenantAliasTree.TenantAlias tenantAlias = tenantAliasTree.getTenants().get(tenant);
            if (tenantAlias != null && tenantAlias.getParent() != null) {
                String parentKey = tenantAlias.getParent().getKey();
                IntermediateConfigState parent = forUpdate.get(parentKey);
                Map<String, Configuration> parentConfigs = getParentConfigs(fullReload, parentKey, parent, state);
                Map<String, Configuration> childConfigs = toChildConfigs(tenant, parentConfigs);
                state.addParentConfigurationByAliases(childConfigs);
            }
        });
        log.info("Apply alias for tenants finished in {} ms", stopWatch.getTime());
    }

    private Map<String, Configuration> getParentConfigs(boolean fullReload, String parentKey,
                                                        IntermediateConfigState parent, IntermediateConfigState child) {
        if (fullReload) {
            if (parent == null) {
                ConfigState parentState = tenantConfigStates.get(parentKey);
                return parentState == null ? Map.of() : parentState.getInmemoryConfigurations();
            }
            return parent.getInmemoryConfigurations();
        }

        if (parent == null) {
            return Map.of();
        }
        Map<String, Configuration> parentConfigsToUpdate = new HashMap<>(parent.getChangedFiles());
        child.getChangedFiles().keySet().stream().filter(it -> !parentConfigsToUpdate.containsKey(it)).forEach(path -> {
            String parentPath = getPathInTenant(path, parentKey);
            Configuration config = parent.getInmemoryConfigurations().get(parentPath);
            parentConfigsToUpdate.put(parentPath, new Configuration(parentPath, config.getContent()));
        });
        return parentConfigsToUpdate;
    }

    private Map<String, Configuration> toChildConfigs(String tenant, Map<String, Configuration> updatedConfigs) {
        Map<String, Configuration> result = new HashMap<>();
        updatedConfigs.forEach((path, config) -> {
            String tenantPath = getPathInTenant(path, tenant);
            result.put(tenantPath, new Configuration(tenantPath, config.getContent()));
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
            ConfigState state = tenantConfigStates.get(tenant);
            Map<String, Configuration> tenantConfigs = state == null? emptyMap() : configType.apply(state);
            configs.stream()
                .map(tenantConfigs::get)
                .filter(Objects::nonNull)
                .forEach(result::add);
        });
        return result;
    }

    private List<Configuration> getByAntPatternPaths(Collection<String> antPatternPaths) {
        return getProcessedConfigs().values().stream().filter(configuration -> {
                    final String configPath = configuration.getPath();
                    return antPatternPaths.stream().anyMatch(patternPath -> antPathMatcher.match(patternPath, configPath));
                })
                .toList();
    }

}
