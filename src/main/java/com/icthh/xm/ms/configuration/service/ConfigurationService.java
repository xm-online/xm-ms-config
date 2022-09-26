package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

import com.icthh.xm.commons.config.client.api.AbstractConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.icthh.xm.ms.configuration.service.dto.ConfigurationHashSum;
import com.icthh.xm.ms.configuration.service.dto.ConfigurationsHashSumDto;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@Primary
@Service
public class ConfigurationService extends AbstractConfigService implements InitializingBean {

    private final DistributedConfigRepository repositoryProxy;
    private final TenantContextHolder tenantContextHolder;
    private final DistributedConfigRepository inMemoryRepository;

    @Override
    @LoggingAspectConfig(resultDetails = false)
    public Map<String, Configuration> getConfigurationMap(String commit) {
        return inMemoryRepository.getMap(commit);
    }

    @Override
    @LoggingAspectConfig(resultDetails = false, inputCollectionAware = true)
    public Map<String, Configuration> getConfigurationMap(String commit, Collection<String> paths) {
        Map<String, Configuration> map = getConfigurationMap(commit);
        Map<String, Configuration> resultMap = new HashMap<>();
        paths.forEach(path -> resultMap.put(path, map.get(path)));
        return resultMap;
    }

    public Optional<Configuration> findConfiguration(String path, String version) {
        return Optional.ofNullable(repositoryProxy.find(path, version).getData());
    }

    public Optional<Configuration> findProcessedConfiguration(String path, Boolean processed) {
        if (Boolean.TRUE.equals(processed)) {
            return Optional.ofNullable(getConfigurationMap(null, List.of(path)).get(path));
        }
        return findConfiguration(path);
    }

    public Optional<Configuration> findConfiguration(String path) {
        return findConfiguration(path, null);
    }

    public Map<String, Configuration> findConfigurations(List<String> paths, Boolean fetchAll) {
        List<Configuration> actualConfigs = getActualConfigs();
        Set<String> pathsSet = new HashSet<>(paths);
        return actualConfigs.stream()
            .filter(config -> fetchAll || pathsSet.contains(config.getPath()))
            .collect(Collectors.toMap(Configuration::getPath, Function.identity()));
    }

    @Override
    public void afterPropertiesSet() {
        repositoryProxy.refreshInternal();
    }

    public void createConfigurations(List<MultipartFile> files) {
        List<Configuration> configurations = files.stream().map(this::toConfiguration).collect(toList());
        repositoryProxy.saveAll(configurations);
    }

    public void updateConfiguration(Configuration configuration) {
        updateConfiguration(configuration, null);
    }

    public void updateConfiguration(Configuration configuration, String oldConfigHash) {
        repositoryProxy.save(configuration, oldConfigHash);
    }

    public void updateConfigurationInMemory(Configuration configuration) {
        inMemoryRepository.updateConfigurationInMemory(configuration, inMemoryRepository.getCommitVersion());
    }

    public void updateConfigurationsInMemory(List<MultipartFile> files) {
        List<Configuration> configurations = files.stream().map(this::toConfiguration).collect(toList());
        inMemoryRepository.updateConfigurationsInMemory(configurations, inMemoryRepository.getCommitVersion());
    }

    public void deleteConfiguration(String path) {
        repositoryProxy.delete(path);
    }

    public void deleteConfigurations(List<String> paths){
        repositoryProxy.deleteAll(paths);
    }

    public void refreshConfiguration() {
        repositoryProxy.refreshAll();
    }

    public void refreshConfiguration(String path) {
        repositoryProxy.refreshPath(path);
    }

    public void refreshTenantConfigurations() {
        repositoryProxy.refreshTenant(getRequiredTenantKeyValue(tenantContextHolder));
    }

    public void refreshTenantConfigurations(String tenantKey) {
        repositoryProxy.refreshTenant(tenantKey);
    }

    @SneakyThrows
    private Configuration toConfiguration(MultipartFile file) {
        String path = StringUtils.replaceChars(file.getOriginalFilename(), File.separator, "/");
        return new Configuration(path, IOUtils.toString(file.getInputStream(), UTF_8));
    }

    public String getVersion() {
        return inMemoryRepository.getCommitVersion();
    }

    public void deleteConfigurationInMemory(List<String> paths) {
        inMemoryRepository.deleteAllInMemory(paths);
    }

    public void recloneConfiguration() {
        repositoryProxy.recloneConfiguration();
    }

    public ConfigurationsHashSumDto findConfigurationsHashSum(String tenant) {
        List<Configuration> actualConfigs = getActualConfigs();

        return new ConfigurationsHashSumDto(actualConfigs.stream()
            .filter(config -> config.getPath().startsWith(getTenantPathPrefix(tenant) + "/"))
            .map(config -> new ConfigurationHashSum(config.getPath(), sha256Hex(config.getContent())))
            .collect(toList()));
    }

    public ConfigurationsHashSumDto findConfigurationsHashSum() {
        return findConfigurationsHashSum(getRequiredTenantKeyValue(tenantContextHolder));
    }

    public void updateConfigurationsFromList(List<Configuration> configs) {
        repositoryProxy.saveOrDeleteEmpty(configs);
        refreshTenantConfigurations();
    }

    @SneakyThrows
    public String updateConfigurationsFromZip(MultipartFile zipFile) {
        return repositoryProxy.setRepositoryState(unzip(new ZipInputStream(zipFile.getInputStream())));
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

    private List<Configuration> getActualConfigs() {
        ConfigurationList configurationList = repositoryProxy.findAll();
        return configurationList.getData();
    }

}
