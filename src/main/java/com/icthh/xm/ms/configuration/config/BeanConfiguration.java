package com.icthh.xm.ms.configuration.config;

import com.hazelcast.core.HazelcastInstance;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.request.spring.config.XmRequestContextConfiguration;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.repository.JGitRepository;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@Import(XmRequestContextConfiguration.class)
public class BeanConfiguration {

    public static final String TENANT_CONFIGURATION_LOCK = "tenant-configuration-lock";

    @Bean(destroyMethod = "destroy")
    public PersistenceConfigRepository jGitRepository(ApplicationProperties applicationProperties,
                                                      @Qualifier(TENANT_CONFIGURATION_LOCK) Lock lock,
                                                      TenantContextHolder tenantContextHolder,
                                                      XmAuthenticationContextHolder authenticationContextHolder,
                                                      XmRequestContextHolder requestContextHolder) {
        return new JGitRepository(applicationProperties.getGit(), lock,
                                  tenantContextHolder, authenticationContextHolder, requestContextHolder);
    }

    @Bean
    @Qualifier(TENANT_CONFIGURATION_LOCK)
    @ConditionalOnProperty(value = "application.config-distribution-mode", havingValue = "hazelcast")
    public Lock gitRepositoryLock(HazelcastInstance hazelcastInstance) {
        return hazelcastInstance.getLock(TENANT_CONFIGURATION_LOCK);
    }

    @Bean
    @Qualifier(TENANT_CONFIGURATION_LOCK)
    @ConditionalOnMissingBean
    public Lock repositoryLock() {
        return new ReentrantLock();
    }
}
