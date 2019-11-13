package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.icthh.xm.commons.config.client.api.AbstractConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public Optional<Configuration> findConfiguration(String path) {
        return Optional.ofNullable(repositoryProxy.find(path).getData());
    }

    public Optional<Configuration> findConfiguration(String path, String version) {
        return Optional.ofNullable(repositoryProxy.find(path, version).getData());
    }

    public List<Configuration> getConfigurations() {
        return repositoryProxy.findAll().getData();
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
}
