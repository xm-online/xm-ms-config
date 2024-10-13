package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static com.icthh.xm.ms.configuration.config.BeanConfiguration.UPDATE_BY_COMMIT_LOCK;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;
import static com.icthh.xm.ms.configuration.domain.ConfigVersion.UNDEFINED_VERSION;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

import com.icthh.xm.commons.config.client.api.AbstractConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.service.dto.ConfigurationHashSum;
import com.icthh.xm.ms.configuration.service.dto.ConfigurationsHashSumDto;
import com.icthh.xm.ms.configuration.utils.LockUtils;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Primary
@Service
public class ConfigurationService extends AbstractConfigService implements InitializingBean {

    private final MemoryConfigStorage memoryStorage;
    private final PersistenceConfigRepository persistenceRepository;
    private final ConfigTopicProducer configTopicProducer;
    private final TenantContextHolder tenantContextHolder;
    private final ApplicationProperties applicationProperties;
    private final ConfigVersionDeserializer configVersionDeserializer;
    private final VersionCache version;
    private final Lock lock;

    public ConfigurationService(MemoryConfigStorage memoryStorage,
                                @Qualifier("configRepository")
                                PersistenceConfigRepository persistenceRepository,
                                ConfigTopicProducer configTopicProducer,
                                TenantContextHolder tenantContextHolder,
                                ApplicationProperties applicationProperties,
                                ConfigVersionDeserializer configVersionDeserializer,
                                VersionCache version,
                                @Qualifier(UPDATE_BY_COMMIT_LOCK)
                                Lock lock) {
        this.memoryStorage = memoryStorage;
        this.persistenceRepository = persistenceRepository;
        this.configTopicProducer = configTopicProducer;
        this.tenantContextHolder = tenantContextHolder;
        this.applicationProperties = applicationProperties;
        this.configVersionDeserializer = configVersionDeserializer;
        this.version = version;
        this.lock = lock;
    }

    @Override
    @LoggingAspectConfig(resultDetails = false)
    public Map<String, Configuration> getConfigurationMap(String version) {
        alignVersion(version);
        return memoryStorage.getProcessedConfigs();
    }

    @Override
    @LoggingAspectConfig(resultDetails = false, inputCollectionAware = true)
    public Map<String, Configuration> getConfigurationMap(String version, Collection<String> paths) {
        alignVersion(version);
        return memoryStorage.getProcessedConfigs(paths);
    }

    private boolean isOnCommit(ConfigVersion version) {
        return  version == null || UNDEFINED_VERSION.equals(version)
            || this.version.containsVersion(version)
            || persistenceRepository.hasVersion(version);
    }

    private void alignVersion(String version) {
        ConfigVersion configVersion = configVersionDeserializer.from(version);
        if (isOnCommit(configVersion)) {
            log.info("Get configuration from memory by commit: {}", configVersion);
        } else {
            updateConfig(configVersion);
        }
    }

    private void updateConfig(ConfigVersion version) {
        LockUtils.runWithLock(lock, applicationProperties.getUpdateConfigWaitTimeSecond(), () -> {
            if (isOnCommit(version)) {
                log.info("Configuration already actual by commit: {}", version);
                return;
            }

            log.info("Load actual configuration from git by commit: {}", version);
            ConfigurationList configurationList = persistenceRepository.findAll();
            List<Configuration> actualConfigs = configurationList.getData();
            memoryStorage.replaceByConfiguration(actualConfigs);
            this.version.addVersion(configurationList.getVersion());
        });
    }

    public Optional<Configuration> findConfiguration(String path, ConfigVersion version) {
        if (version == null || UNDEFINED_VERSION.equals(version)) {
            return memoryStorage.getConfig(path);
        }
        log.debug("Get configuration from storage by path {} and version {}", path, version);
        return Optional.ofNullable(persistenceRepository.find(path, version));
    }

    public Optional<Configuration> findProcessedConfiguration(String path, Boolean processed) {
        if (Boolean.TRUE.equals(processed)) {
            return memoryStorage.getProcessedConfig(path);
        }
        return findConfiguration(path);
    }

    public Optional<Configuration> findConfiguration(String path) {
        return memoryStorage.getConfig(path);
    }

    public Map<String, Configuration> findTenantConfigurations(List<String> paths, Boolean fetchAll) {
        if (!fetchAll && paths.isEmpty()) {
            return Map.of();
        }

        if (fetchAll) {
            return memoryStorage.getConfigsFromTenant(getRequiredTenantKeyValue(tenantContextHolder)).stream()
                .collect(toMap(Configuration::getPath, identity()));
        } else {
            return memoryStorage.getConfigs(paths).stream().collect(toMap(Configuration::getPath, identity()));
        }
    }

    @Override
    public void afterPropertiesSet() {
        refreshConfiguration();
    }

    private void notifyChanged(ConfigVersion commit, Collection<String> updated) {
        configTopicProducer.notifyConfigurationChanged(commit, new ArrayList<>(updated));
    }

    public void saveConfigurations(List<Configuration> configurations, Map<String, String> configHashes) {
        ConfigVersion configVersion = persistenceRepository.saveAll(configurations, configHashes);
        Set<String> updatedConfigs = memoryStorage.saveConfigs(configurations);
        version.addVersion(configVersion);
        notifyChanged(configVersion, updatedConfigs);
    }

    public void createConfigurations(List<MultipartFile> files) {
        List<Configuration> configurations = files.stream().map(this::toConfiguration).collect(toList());
        saveConfigurations(configurations, Map.of());
    }

    public void updateConfiguration(Configuration configuration) {
        saveConfigurations(List.of(configuration), Map.of());
    }

    public void updateConfiguration(Configuration configuration, String oldConfigHash) {
        saveConfigurations(List.of(configuration), Map.of(configuration.getPath(), oldConfigHash));
    }

    public void updateConfigurationInMemory(List<Configuration> configurations) {
        Set<String> updated = memoryStorage.saveConfigs(configurations);
        notifyChanged(version.getLastVersion(), updated);
    }

    public void updateConfigurationsInMemory(List<MultipartFile> files) {
        List<Configuration> configurations = files.stream().map(this::toConfiguration).collect(toList());
        updateConfigurationInMemory(configurations);
    }

    public void deleteConfiguration(String path) {
        deleteConfigurations(List.of(path));
    }

    public void deleteConfigurations(List<String> paths){
        ConfigVersion configVersion = persistenceRepository.deleteAll(paths);
        Set<String> updated = memoryStorage.remove(paths);
        version.addVersion(configVersion);
        notifyChanged(configVersion, updated);
    }

    public void deleteConfigurationInMemory(List<String> paths) {
        Set<String> updated = memoryStorage.remove(paths);
        notifyChanged(version.getLastVersion(), updated);
    }

    public void refreshConfiguration() {
        ConfigurationList configurationList = persistenceRepository.findAll();
        Set<String> updated = memoryStorage.replaceByConfiguration(configurationList.getData());
        version.addVersion(configurationList.getVersion());
        notifyChanged(configurationList.getVersion(), updated);
    }

    public void refreshConfiguration(String path) {
        ConfigurationItem configuration = persistenceRepository.find(path);
        Set<String> updated = memoryStorage.saveConfigs(List.of(configuration.getData()));
        version.addVersion(configuration.getVersion());
        notifyChanged(configuration.getVersion(), updated);
    }

    public void refreshTenantConfigurations() {
        refreshTenantConfigurations(getRequiredTenantKeyValue(tenantContextHolder));
    }

    public void refreshTenantConfigurations(String tenantKey) {
        var configurationList = persistenceRepository.findAllInDirectory(TENANT_PREFIX + "/" + tenantKey);
        Set<String> updated = memoryStorage.replaceByConfigurationInTenant(configurationList.getData(), tenantKey);
        version.addVersion(configurationList.getVersion());
        notifyChanged(configurationList.getVersion(), updated);
    }

    @SneakyThrows
    private Configuration toConfiguration(MultipartFile file) {
        String path = StringUtils.replaceChars(file.getOriginalFilename(), File.separator, "/");
        return new Configuration(path, IOUtils.toString(file.getInputStream(), UTF_8));
    }

    public ConfigVersion getVersion() {
        return version.getLastVersion();
    }

    public void recloneConfiguration() {
        persistenceRepository.recloneConfiguration();
        refreshConfiguration();
    }

    public ConfigurationsHashSumDto findConfigurationsHashSum(String tenant) {
        List<Configuration> actualConfigs = memoryStorage.getConfigsFromTenant(tenant);

        return new ConfigurationsHashSumDto(actualConfigs.stream()
            .map(config -> new ConfigurationHashSum(config.getPath(), sha256Hex(config.getContent())))
            .collect(toList()));
    }

    public ConfigurationsHashSumDto findConfigurationsHashSum() {
        return findConfigurationsHashSum(getRequiredTenantKeyValue(tenantContextHolder));
    }

    public void updateConfigurationsFromList(List<Configuration> configs) {
        configs = configs.stream().filter(this::isConfigUnderTenant).collect(toList());
        saveConfigurations(configs, Map.of());
    }

    @SneakyThrows
    public ConfigVersion updateConfigurationsFromZip(MultipartFile zipFile) {
        ConfigVersion configVersion = persistenceRepository.setRepositoryState(unzip(new ZipInputStream(zipFile.getInputStream())));
        refreshConfiguration();
        return configVersion;
    }

    @SneakyThrows
    public static List<Configuration> unzip(final ZipInputStream zipInputStream) {
        List<Configuration> configurations = new ArrayList<>();

        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            String name = entry.getName();
            int beginIndex = name.indexOf("config/");
            if (beginIndex < 0) {
                log.warn("Skip {} file. It's not under /config folder", name);
                continue;
            }
            if (entry.isDirectory()) {
                continue;
            }

            String path = "/" + name.substring(beginIndex);
            configurations.add(new Configuration(path, IOUtils.toString(zipInputStream, UTF_8)));
        }

        return configurations;
    }

    private Boolean isConfigUnderTenant(Configuration config, String tenant) {
        Path path = Path.of("/", config.getPath()).normalize();
        return path.startsWith(getTenantPathPrefix(tenant) + "/");
    }

    private Boolean isConfigUnderTenant(Configuration config) {
        return isConfigUnderTenant(config, getRequiredTenantKeyValue(tenantContextHolder));
    }

    public boolean isAdminRefreshAvailable() {
        List<String> superTenantsList = applicationProperties.getSuperTenantsList();
        superTenantsList = superTenantsList != null ? superTenantsList : Collections.emptyList();
        return superTenantsList.contains(tenantContextHolder.getTenantKey().toUpperCase());
    }

    public void assertAdminRefreshAvailable() {
        if (!isAdminRefreshAvailable()) {
            throw new AccessDeniedException("Admin refresh config not available for tenant " + tenantContextHolder.getTenantKey());
        }
    }

    public void refreshTenantsConfigurations(List<String> tenants) {
        List<String> changed = new ArrayList<>();
        tenants.forEach(tenantKey -> {
            var configurationList = persistenceRepository.findAllInDirectory(TENANT_PREFIX + "/" + tenantKey);
            version.addVersion(configurationList.getVersion());
            var updated = memoryStorage.replaceByConfigurationInTenant(configurationList.getData(), tenantKey);
            changed.addAll(updated);
        });
        notifyChanged(version.getLastVersion(), changed);
    }
}
