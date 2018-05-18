package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.utils.ConfigPathUtils.getTenantPathPrefix;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.domain.Configuration;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.service.processors.ConfigurationProcessor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ConfigurationService {

    private final DistributedConfigRepository distributedConfigRepository;

    private final PersistenceConfigRepository persistenceConfigRepository;

    private final TenantContextHolder tenantContextHolder;

    private final List<ConfigurationProcessor> configurationProcessors;

    public ConfigurationService(DistributedConfigRepository distributedConfigRepository,
                                PersistenceConfigRepository persistenceConfigRepository,
                                TenantContextHolder tenantContextHolder,
                                List<ConfigurationProcessor> configurationProcessors) {
        this.distributedConfigRepository = distributedConfigRepository;
        this.persistenceConfigRepository = persistenceConfigRepository;
        this.tenantContextHolder = tenantContextHolder;
        this.configurationProcessors = configurationProcessors;

        refreshConfigurations();
    }

    public void createConfiguration(Configuration configuration) {
        persistenceConfigRepository.save(configuration);
        distributedConfigRepository.save(process(configuration));
    }

    private Configuration process(Configuration configuration) {
        for(ConfigurationProcessor processor: configurationProcessors) {
            configuration = processor.processConfiguration(configuration);
        }
        return configuration;
    }

    private List<Configuration> process(List<Configuration> configurations) {
        for(ConfigurationProcessor processor: configurationProcessors) {
            configurations = processor.processConfigurations(configurations);
        }
        return configurations;
    }

    public void updateConfiguration(Configuration configuration) {
        updateConfiguration(process(configuration), null);
    }

    public void updateConfiguration(Configuration configuration, String oldConfigHash) {
        persistenceConfigRepository.save(configuration, oldConfigHash);
        distributedConfigRepository.save(process(configuration));
    }

    public Optional<Configuration> findConfiguration(String path) {
        return Optional.ofNullable(distributedConfigRepository.find(path));
    }

    public void deleteConfiguration(String path) {
        persistenceConfigRepository.delete(path);
        distributedConfigRepository.delete(path);
    }

    public void refreshConfigurations() {
        List<Configuration> actualConfigs = persistenceConfigRepository.findAll();
        List<String> oldKeys = distributedConfigRepository.getKeysList();
        actualConfigs.forEach(config -> oldKeys.remove(config.getPath()));
        oldKeys.forEach(distributedConfigRepository::delete);
        distributedConfigRepository.saveAll(process(actualConfigs));
    }

    public void createConfigurations(List<MultipartFile> files) {
        List<Configuration> configurations = files.stream().map(this::toConfiguration).collect(toList());
        persistenceConfigRepository.saveAll(configurations);
        distributedConfigRepository.saveAll(process(configurations));
    }

    @SneakyThrows
    private Configuration toConfiguration(MultipartFile file) {
        return new Configuration(file.getOriginalFilename(), IOUtils.toString(file.getInputStream(), UTF_8));
    }

    public void refreshConfigurations(String path) {
        Configuration configuration = persistenceConfigRepository.find(path);
        distributedConfigRepository.save(process(configuration));
    }

    public void refreshTenantConfigurations() {
        List<Configuration> actualConfigs = persistenceConfigRepository.findAll();
        actualConfigs = actualConfigs.stream()
                .filter(config -> config.getPath().startsWith(getTenantPathPrefix(tenantContextHolder)))
                .collect(toList());

        List<String> allOldKeys = distributedConfigRepository.getKeysList();
        List<String> oldKeys = allOldKeys.stream()
                .filter(path -> path.startsWith(getTenantPathPrefix(tenantContextHolder)))
                .collect(toList());

        actualConfigs.forEach(config -> oldKeys.remove(config.getPath()));
        oldKeys.forEach(distributedConfigRepository::delete);
        distributedConfigRepository.saveAll(process(actualConfigs));
    }
}
