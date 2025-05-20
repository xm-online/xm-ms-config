package com.icthh.xm.ms.configuration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.client.service.TenantAliasService;
import com.icthh.xm.commons.config.domain.TenantAliasTree;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.request.spring.config.XmRequestContextConfiguration;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.JGitRepository;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorageExcludeConfigDecorator;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorageImpl;
import com.icthh.xm.ms.configuration.service.FileService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeStorage;
import com.icthh.xm.ms.configuration.service.processors.TenantConfigurationProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Configuration
@Import(XmRequestContextConfiguration.class)
public class BeanConfiguration {

    public static final String TENANT_CONFIGURATION_LOCK = "tenant-configuration-lock";
    public static final String UPDATE_BY_COMMIT_LOCK = "update-by-commit-lock";
    public static final String UPDATE_IN_MEMORY = "in-memory-update-lock";

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    @Bean
    public PersistenceConfigRepository configRepository(ApplicationProperties applicationProperties,
                                                        @Qualifier(TENANT_CONFIGURATION_LOCK) Lock lock,
                                                        TenantContextHolder tenantContextHolder,
                                                        XmAuthenticationContextHolder authenticationContextHolder,
                                                        XmRequestContextHolder requestContextHolder,
                                                        FileService fileService) {
        return new JGitRepository(applicationProperties.getGit(), lock, tenantContextHolder,
            authenticationContextHolder, requestContextHolder, fileService);
    }

    @Bean
    @Qualifier(TENANT_CONFIGURATION_LOCK)
    public Lock gitRepositoryLock() {
        return new ReentrantLock();
    }

    @Bean
    @Qualifier(UPDATE_BY_COMMIT_LOCK)
    public Lock updateByCommitLock() {
        return new ReentrantLock();
    }

    @Bean
    @Qualifier(UPDATE_IN_MEMORY)
    public Lock inmemoryUpdateLock() {
        return new ReentrantLock();
    }

    @Bean
    public MemoryConfigStorage memoryConfigStorage(List<TenantConfigurationProcessor> tenantConfigurationProcessors,
                                                   TenantAliasTreeStorage tenantAliasTreeStorage,
                                                   ApplicationProperties applicationProperties,
                                                   @Qualifier(UPDATE_IN_MEMORY)
                                                   Lock lock) {

        return new MemoryConfigStorageExcludeConfigDecorator(
            new MemoryConfigStorageImpl(
                tenantConfigurationProcessors,
                tenantAliasTreeStorage,
                applicationProperties,
                lock
            ),
            applicationProperties
        );
    }

    @Bean
    public TenantAliasService tenantAliasService(TenantAliasTreeStorage tenantAliasTreeStorage) {
        return new TenantAliasService() {
            @Override
            public TenantAliasTree getTenantAliasTree() {
                return tenantAliasTreeStorage.getTenantAliasTree();
            }

            @Override
            public void onRefresh(String config) {
                // do noting
            }
        };
    }

}
