package com.icthh.xm.ms.configuration.config;

import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.request.spring.config.XmRequestContextConfiguration;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.repository.impl.JGitRepository;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorageExcludeConfigDecorator;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorageImpl;
import com.icthh.xm.ms.configuration.repository.impl.MultiGitRepository;
import com.icthh.xm.ms.configuration.service.TenantAliasService;
import com.icthh.xm.ms.configuration.service.processors.PrivateConfigurationProcessor;
import com.icthh.xm.ms.configuration.service.processors.PublicConfigurationProcessor;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Configuration
@Import(XmRequestContextConfiguration.class)
public class BeanConfiguration {

    public static final String TENANT_CONFIGURATION_LOCK = "tenant-configuration-lock";

    public static final String UPDATE_BY_COMMIT_LOCK = "update-by-commit-lock";

    @Bean
    public PersistenceConfigRepository configRepository(ApplicationProperties applicationProperties,
                                                        @Qualifier(TENANT_CONFIGURATION_LOCK) Lock lock,
                                                        TenantContextHolder tenantContextHolder,
                                                        XmAuthenticationContextHolder authenticationContextHolder,
                                                        XmRequestContextHolder requestContextHolder) {
        JGitRepository jGitRepository = new JGitRepository(applicationProperties.getGit(), lock,
            tenantContextHolder, authenticationContextHolder, requestContextHolder);
        if (Boolean.TRUE.equals(applicationProperties.getMultiRepositoryEnabled())) {
            return new MultiGitRepository(jGitRepository) {
                @Override
                protected PersistenceConfigRepository createExternalRepository(ApplicationProperties.GitProperties gitProperties) {
                    return new JGitRepository(gitProperties, lock, tenantContextHolder, authenticationContextHolder, requestContextHolder);
                }
            };
        }
        return jGitRepository;
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
    public MemoryConfigStorage memoryConfigStorage(List<PrivateConfigurationProcessor> privateConfigurationProcessors,
                                                   List<PublicConfigurationProcessor> publicConfigurationProcessors,
                                                   TenantAliasService tenantAliasService,
                                                   ApplicationProperties applicationProperties) {
        log.info("PrivateConfigurationProcessor {}", privateConfigurationProcessors);
        log.info("PublicConfigurationProcessor {}", publicConfigurationProcessors);

        return new MemoryConfigStorageExcludeConfigDecorator(
                new MemoryConfigStorageImpl(
                        privateConfigurationProcessors,
                        publicConfigurationProcessors,
                        tenantAliasService
                ),
                applicationProperties
        );
    }
}
