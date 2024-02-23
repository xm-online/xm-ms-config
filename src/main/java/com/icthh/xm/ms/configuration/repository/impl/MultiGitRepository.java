package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.icthh.xm.ms.configuration.config.Constants.TENANT_ENV_PATTERN;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_NAME;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

@Slf4j
@RequiredArgsConstructor
public class MultiGitRepository implements PersistenceConfigRepository {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, PersistenceConfigRepository> externalRepositories = new ConcurrentHashMap<>();
    private final PersistenceConfigRepository mainRepository;
    private final AtomicReference<ConfigVersion> version = new AtomicReference<>(ConfigVersion.UNDEFINED_VERSION);

    @Override
    public boolean hasVersion(ConfigVersion version) {
        boolean mainContains = mainRepository.hasVersion(version);
        boolean allTenantsContains = version.getExternalTenantVersions().entrySet().stream()
            .allMatch(e -> {
                PersistenceConfigRepository persistenceConfigRepository = externalRepositories.get(e.getKey());
                return persistenceConfigRepository != null && persistenceConfigRepository.hasVersion(e.getValue());
            });
        return mainContains && allTenantsContains;
    }

    @Override
    public ConfigurationList findAll() {
        ConfigurationList resultList = new ConfigurationList(ConfigVersion.UNDEFINED_VERSION, new ArrayList<>());
        externalRepositories.forEach((tenantKey, repository) -> {
            ConfigurationList externalList = repository.findAll();
            List<Configuration> tenantList = externalList.getData().stream()
                .filter(it -> it.getPath().startsWith(TENANT_PREFIX + tenantKey))
                .collect(toList());
            resultList.getData().addAll(tenantList);
            resultList.addExternalTenantVersion(tenantKey, externalList.getVersion());
        });
        ConfigurationList mainList = mainRepository.findAll();
        resultList.setMainVersion(mainList.getVersion());
        version.set(resultList.getVersion());
        return resultList;
    }

    @Override
    public ConfigurationItem find(String path) {
        TenantRepository tenantRepository = getRepository(path);
        ConfigurationItem configurationItem = tenantRepository.repository.find(path);
        updateVersion(tenantRepository, configurationItem.getVersion());
        return new ConfigurationItem(version.get(), configurationItem.getData());
    }

    @Override
    public Configuration find(String path, ConfigVersion version) {
        return getRepository(path).repository.find(path, version);
    }

    @Override
    public ConfigVersion saveAll(List<Configuration> configurations) {
        getRepositories(configurations, Configuration::getPath).forEach(e -> {
            TenantRepository tenantRepository = e.getKey();
            PersistenceConfigRepository repository = tenantRepository.repository;
            ConfigVersion configVersion = repository.saveAll(e.getValue());
            updateVersion(tenantRepository, configVersion);
        });
        return version.get();
    }

    @Override
    public ConfigVersion setRepositoryState(List<Configuration> configurations) {
        getRepositories(configurations, Configuration::getPath).forEach(e -> {
            TenantRepository tenantRepository = e.getKey();
            ConfigVersion configVersion = tenantRepository.repository.setRepositoryState(e.getValue());
            updateVersion(tenantRepository, configVersion);
        });
        return version.get();
    }

    @Override
    public ConfigVersion save(Configuration configuration) {
        TenantRepository tenantRepository = getRepository(configuration.getPath());
        ConfigVersion save = tenantRepository.repository.save(configuration);
        updateVersion(tenantRepository, save);
        return version.get();
    }

    @Override
    public ConfigVersion save(Configuration configuration, String oldConfigHash) {
        TenantRepository tenantRepository = getRepository(configuration.getPath());
        ConfigVersion save = tenantRepository.repository.save(configuration, oldConfigHash);
        updateVersion(tenantRepository, save);
        return version.get();
    }

    @Override
    public ConfigVersion deleteAll(List<String> paths) {
        getRepositories(paths, identity()).forEach(e -> {
            TenantRepository tenantRepository = e.getKey();
            ConfigVersion configVersion = tenantRepository.repository.deleteAll(e.getValue());
            updateVersion(tenantRepository, configVersion);
        });
        return version.get();
    }

    @Override
    public ConfigVersion delete(String path) {
        TenantRepository tenantRepository = getRepository(path);
        ConfigVersion configVersion = tenantRepository.repository.delete(path);
        updateVersion(tenantRepository, configVersion);
        return version.get();
    }

    @Override
    public ConfigVersion saveOrDeleteEmpty(List<Configuration> configurations) {
        getRepositories(configurations, Configuration::getPath).forEach(e -> {
            TenantRepository tenantRepository = e.getKey();
            ConfigVersion configVersion = tenantRepository.repository.saveOrDeleteEmpty(e.getValue());
            updateVersion(tenantRepository, configVersion);
        });
        return version.get();
    }

    @Override
    public void recloneConfiguration() {
        mainRepository.recloneConfiguration();
        externalRepositories.values().forEach(PersistenceConfigRepository::recloneConfiguration);
    }

    @Override
    public ConfigVersion getCurrentVersion() {
        Map<String, ConfigVersion> tenantVersion = new HashMap<>();
        externalRepositories.forEach((tenantKey, repository) -> tenantVersion.put(tenantKey, repository.getCurrentVersion()));
        ConfigVersion configVersion = new ConfigVersion(mainRepository.getCurrentVersion().getMainVersion(), tenantVersion);
        version.set(configVersion);
        return configVersion;
    }

    private void updateVersion(TenantRepository tenantRepository, ConfigVersion configVersion) {
        Optional<String> tenantKey = tenantRepository.tenantKey;
        if (tenantKey.isPresent()) {
            ConfigVersion newValue = version.get().addExternalTenantVersion(tenantKey.get(), configVersion);
            version.set(newValue);
        } else {
            ConfigVersion newValue = version.get().mainVersion(configVersion);
            version.set(newValue);
        }
    }

    private TenantRepository getRepository(String path) {
        if (isUnderTenant(path)) {
            String tenantKey = getTenantKey(path);
            PersistenceConfigRepository externalRepository = externalRepositories.get(tenantKey);
            if (externalRepository != null) {
                return new TenantRepository(tenantKey, externalRepository);
            }
        }
        return new TenantRepository(mainRepository);
    }

    private <T> Set<Entry<TenantRepository, List<T>>> getRepositories(List<T> items,
                                                                      Function<T, String> getPath) {
        Map<TenantRepository, List<T>> result = new HashMap<>();
        items.forEach(it -> {
            var repository = getRepository(getPath.apply(it));
            result.computeIfAbsent(repository, k -> new ArrayList<>()).add(it);
        });
        return result.entrySet();
    }

    private boolean isUnderTenant(String path) {
        return path.startsWith(TENANT_PREFIX) && pathMatcher.match(TENANT_ENV_PATTERN, path);
    }

    private String getTenantKey(String path) {
        return pathMatcher.extractUriTemplateVariables(TENANT_ENV_PATTERN, path).get(TENANT_NAME);
    }

    private static class TenantRepository {

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private final Optional<String> tenantKey;
        private final PersistenceConfigRepository repository;

        private TenantRepository(String tenantKey, PersistenceConfigRepository repository) {
            this.tenantKey = Optional.ofNullable(tenantKey);
            this.repository = repository;
        }

        private TenantRepository(PersistenceConfigRepository repository) {
            this.tenantKey = Optional.empty();
            this.repository = repository;
        }
    }
}
