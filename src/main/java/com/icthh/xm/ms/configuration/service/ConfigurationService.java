package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.icthh.xm.commons.config.client.api.AbstractConfigService;
import com.icthh.xm.commons.config.client.api.ConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Primary
@Service
public class ConfigurationService extends AbstractConfigService implements InitializingBean {

    private final DistributedConfigRepository repositoryProxy;
    private final TenantContextHolder tenantContextHolder;
    private final DistributedConfigRepository inMemoryRepository;

    @Override
    public Map<String, Configuration> getConfigurationMap(String commit) {
        return inMemoryRepository.getMap(commit);
    }

    @Override
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

    public Optional<Configuration> findConfiguration(String path) {
        return Optional.ofNullable(repositoryProxy.find(path).getData());
    }

    public List<Configuration> getConfigurations() {
        return repositoryProxy.findAll().getData();
    }

    public void deleteConfiguration(String path) {
        repositoryProxy.delete(path);
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
        return new Configuration(file.getOriginalFilename(), IOUtils.toString(file.getInputStream(), UTF_8));
    }
}
