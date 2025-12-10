package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicConfigRepository implements PersistenceConfigRepository {

    private static final int DYNAMIC_PRIORITY = -1;

    private final List<PersistenceConfigRepository> repositories;
    private final PersistenceConfigRepository lowestPriorityRepository;

    public DynamicConfigRepository(List<PersistenceConfigRepository> repositories) {
        // Sort by priority (lower number = higher priority)
        this.repositories = repositories.stream()
                .sorted(Comparator.comparingInt(PersistenceConfigRepository::priority))
                .toList();

        // Lowest priority repo (highest number) is used for version operations
        this.lowestPriorityRepository = repositories.stream()
                .max(Comparator.comparingInt(PersistenceConfigRepository::priority))
                .orElseThrow(() -> new IllegalArgumentException("At least one repository must be provided"));

        log.info("DynamicConfigRepository initialized with {} repositories, lowest priority: {}",
                repositories.size(), lowestPriorityRepository.getClass().getSimpleName());
    }

    @Override
    public String type() {
        return "DYNAMIC";
    }

    @Override
    public int priority() {
        return DYNAMIC_PRIORITY;
    }

    @Override
    public boolean isApplicable(String path) {
        // Dynamic repository handles all paths
        return true;
    }

    private PersistenceConfigRepository getRepositoryForPath(String path) {
        return repositories.stream()
                .filter(repo -> repo.isApplicable(path))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No repository found for path: " + path));
    }

    @Override
    public boolean hasVersion(ConfigVersion version) {
        return lowestPriorityRepository.hasVersion(version);
    }

    @Override
    public ConfigurationList findAll() {
        return fetchFromRepositories(PersistenceConfigRepository::findAll);
    }

    @Override
    public ConfigurationList findAllInTenant(String tenantKey) {
        return fetchFromRepositories(repository -> repository.findAllInTenant(tenantKey));
    }

    @Override
    public ConfigurationList findAllInTenants(Set<String> tenants) {
        return fetchFromRepositories(repository -> repository.findAllInTenants(tenants));
    }

    private ConfigurationList fetchFromRepositories(
            Function<PersistenceConfigRepository, ConfigurationList> findConfigurationsQuery) {
        // Fetch from all repositories
        var allConfigurations = repositories.stream()
                .map(findConfigurationsQuery)
                .toList();

        // Get version from lowest priority repository
        var version = findConfigurationsQuery.apply(lowestPriorityRepository).getVersion();

        // Merge configurations by priority
        return mergeConfigurationLists(allConfigurations, version);
    }

    @Override
    public ConfigurationItem find(String path) {
        return getRepositoryForPath(path).find(path);
    }

    @Override
    public Configuration find(String path, ConfigVersion version) {
        return getRepositoryForPath(path).find(path, version);
    }

    @Override
    public ConfigVersion save(Configuration configuration) {
        return getRepositoryForPath(configuration.getPath()).save(configuration);
    }

    @Override
    public ConfigVersion saveAll(List<Configuration> configurations, Map<String, String> configHashes) {
        // Group configurations by repository
        var configsByRepo = configurations.stream()
                .collect(Collectors.groupingBy(config -> getRepositoryForPath(config.getPath())));

        var version = ConfigVersion.UNDEFINED_VERSION;
        for (var entry : configsByRepo.entrySet()) {
            version = entry.getKey().saveAll(entry.getValue(), configHashes);
        }
        return version;
    }

    @Override
    public ConfigVersion setRepositoryState(List<Configuration> configurations) {
        // Group configurations by repository
        var configsByRepo = configurations.stream()
                .collect(Collectors.groupingBy(config -> getRepositoryForPath(config.getPath())));

        var version = ConfigVersion.UNDEFINED_VERSION;
        for (var entry : configsByRepo.entrySet()) {
            version = entry.getKey().setRepositoryState(entry.getValue());
        }
        return version;
    }

    @Override
    public ConfigVersion deleteAll(List<String> paths) {
        // Group paths by repository
        var pathsByRepo = paths.stream()
                .collect(Collectors.groupingBy(this::getRepositoryForPath));

        var version = ConfigVersion.UNDEFINED_VERSION;
        for (var entry : pathsByRepo.entrySet()) {
            version = entry.getKey().deleteAll(entry.getValue());
        }
        return version;
    }

    @Override
    public void recloneConfiguration() {
        repositories.forEach(PersistenceConfigRepository::recloneConfiguration);
    }

    @Override
    public ConfigVersion getCurrentVersion() {
        return lowestPriorityRepository.getCurrentVersion();
    }

    private ConfigurationList mergeConfigurationLists(List<ConfigurationList> configurationLists, ConfigVersion version) {
        // Create a map to hold configurations by path, with priority-based override
        Map<String, Configuration> mergedConfigs = configurationLists.stream()
                .flatMap(list -> list.getData().stream())
                .collect(Collectors.toMap(
                        Configuration::getPath,
                        config -> config,
                        (config1, config2) -> {
                            // When there's a conflict, keep the one from the repository with higher priority
                            // (the first one encountered, since repositories are already sorted)
                            return config1;
                        }
                ));

        return new ConfigurationList(version, mergedConfigs.values().stream().toList());
    }
}
