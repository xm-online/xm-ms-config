package com.icthh.xm.ms.configuration.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.domain.ExternalTenantsConfig;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.icthh.xm.ms.configuration.config.Constants.TENANT_ENV_PATTERN;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_NAME;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Slf4j
@RequiredArgsConstructor
public abstract class MultiGitRepository implements PersistenceConfigRepository {

    public final static String EXTERNAL_TENANTS_CONFIG = "/config/tenants/external-tenants.yml";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    private final PersistenceConfigRepository mainRepository;
    private final AtomicReference<ConfigVersion> version = new AtomicReference<>(ConfigVersion.UNDEFINED_VERSION);
    private volatile Map<String, PersistenceConfigRepository> externalRepositories = emptyMap();
    private volatile ExternalTenantsConfig externalTenantsConfig;
    private volatile String externalTenantsConfigString;

    @Override
    public boolean hasVersion(ConfigVersion version) {
        boolean mainContains = mainRepository.hasVersion(version);
        boolean saveExternalTenants = externalRepositories.keySet().equals(version.getExternalTenantVersions().keySet());
        boolean allTenantsContains = externalRepositories.isEmpty() || version.getExternalTenantVersions()
            .entrySet().stream()
            .allMatch(e -> {
                PersistenceConfigRepository persistenceConfigRepository = externalRepositories.get(e.getKey());
                return persistenceConfigRepository != null && persistenceConfigRepository.hasVersion(e.getValue());
            });
        return mainContains && saveExternalTenants && allTenantsContains;
    }

    @Override
    public ConfigurationList findAll() {
        ConfigurationList resultList = new ConfigurationList(ConfigVersion.UNDEFINED_VERSION, new ArrayList<>());

        ConfigurationList mainList = mainRepository.findAll();
        handleMultiGitRepository(mainList.getData(), true);

        externalRepositories.forEach((tenantKey, repository) -> {
            ConfigurationList externalList = repository.findAll();
            List<Configuration> tenantList = externalList.getData().stream()
                .filter(it -> it.getPath().startsWith(TENANT_PREFIX + tenantKey))
                .collect(toList());
            resultList.getData().addAll(tenantList);
            resultList.addExternalTenantVersion(tenantKey, externalList.getVersion());
        });
        resultList.getData().addAll(mainList.getData());
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
        handleMultiGitRepository(configurations, false);
        getRepositories(configurations, Configuration::getPath).forEach(e -> {
            TenantRepository tenantRepository = e.getKey();
            PersistenceConfigRepository repository = tenantRepository.repository;
            ConfigVersion configVersion = repository.saveAll(e.getValue());
            updateVersion(tenantRepository, configVersion);
        });
        return version.get();
    }

    // work only with main repository
    @Override
    public ConfigVersion setRepositoryState(List<Configuration> configurations) {
        handleMultiGitRepository(configurations, true);
        mainRepository.setRepositoryState(configurations);
        return version.get();
    }

    @Override
    public ConfigVersion save(Configuration configuration) {
        handleMultiGitRepository(configuration);
        TenantRepository tenantRepository = getRepository(configuration.getPath());
        ConfigVersion save = tenantRepository.repository.save(configuration);
        updateVersion(tenantRepository, save);
        return version.get();
    }

    @Override
    public ConfigVersion save(Configuration configuration, String oldConfigHash) {
        handleMultiGitRepository(configuration);
        TenantRepository tenantRepository = getRepository(configuration.getPath());
        ConfigVersion save = tenantRepository.repository.save(configuration, oldConfigHash);
        updateVersion(tenantRepository, save);
        return version.get();
    }

    @Override
    public ConfigVersion deleteAll(List<String> paths) {
        if (paths.stream().anyMatch(EXTERNAL_TENANTS_CONFIG::equals)) {
            handleMultiGitRepository(new Configuration(EXTERNAL_TENANTS_CONFIG, ""));
        }
        getRepositories(paths, identity()).forEach(e -> {
            TenantRepository tenantRepository = e.getKey();
            ConfigVersion configVersion = tenantRepository.repository.deleteAll(e.getValue());
            updateVersion(tenantRepository, configVersion);
        });
        return version.get();
    }

    @Override
    public ConfigVersion delete(String path) {
        handleMultiGitRepository(new Configuration(path, ""));
        TenantRepository tenantRepository = getRepository(path);
        ConfigVersion configVersion = tenantRepository.repository.delete(path);
        updateVersion(tenantRepository, configVersion);
        return version.get();
    }

    @Override
    public ConfigVersion saveOrDeleteEmpty(List<Configuration> configurations) {
        handleMultiGitRepository(configurations, false);
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

    private void handleMultiGitRepository(List<Configuration> configurations, boolean fullUpdate) {
        List<Configuration> configs = firstNonNull(configurations, emptyList());
        configs.stream()
            .filter(it -> EXTERNAL_TENANTS_CONFIG.equals(it.getPath()))
            .findAny()
            .ifPresentOrElse(this::handleMultiGitRepository, () -> {
                if (fullUpdate) {
                    log.info("External tenants config not found");
                    this.externalRepositories = emptyMap();
                }
            });
    }

    @SneakyThrows
    private void handleMultiGitRepository(Configuration configuration) {
        if (!EXTERNAL_TENANTS_CONFIG.equals(configuration.getPath())) {
            return;
        }

        String newExternalTenantsConfigString = configuration.getContent();
        if (newExternalTenantsConfigString.equals(externalTenantsConfigString)) {
            log.info("External tenants config not changed");
            return;
        }

        externalTenantsConfigString = newExternalTenantsConfigString;
        if (StringUtils.isNotBlank(externalTenantsConfigString)) {
            externalTenantsConfig = objectMapper.readValue(externalTenantsConfigString, ExternalTenantsConfig.class);
        } else {
            externalTenantsConfig = new ExternalTenantsConfig();
        }

        Map<String, PersistenceConfigRepository> newExternalRepositories = new HashMap<>();
        externalTenantsConfig.getExternalTenants().forEach((tenantKey, gitProperties) -> {
            String tenantPath = gitProperties.getUri();
            if (tenantPath != null) {
                log.info("Create external tenant repository for tenant: {}", tenantKey);
                newExternalRepositories.put(tenantKey, createExternalRepository(gitProperties));
            }
        });
        this.externalRepositories = newExternalRepositories;
        if (this.externalRepositories.isEmpty()) {
            version.set(new ConfigVersion(version.get().getMainVersion()));
        }
        log.info("External tenants config updated: {}", externalTenantsConfig.getExternalTenants().keySet());
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

    protected abstract PersistenceConfigRepository createExternalRepository(GitProperties gitProperties);

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

    @EqualsAndHashCode
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
