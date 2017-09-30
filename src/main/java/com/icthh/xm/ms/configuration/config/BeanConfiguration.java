package com.icthh.xm.ms.configuration.config;

import com.hazelcast.core.HazelcastInstance;
import com.icthh.xm.ms.configuration.repository.JGitRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.locks.Lock;

@Configuration
public class BeanConfiguration {

    public static final String TENANT_CONFIGURATION_LOCK = "tenant-configuration-lock";

    @Bean(destroyMethod = "destroy")
    public JGitRepository jGitRepository(ApplicationProperties applicationProperties,
                                         @Qualifier(TENANT_CONFIGURATION_LOCK) Lock lock) {
        return new JGitRepository(applicationProperties.getGit(), lock);
    }

    @Bean
    @Qualifier(TENANT_CONFIGURATION_LOCK)
    public Lock gitRepositoryLock(HazelcastInstance hazelcastInstance) {
        return hazelcastInstance.getLock(TENANT_CONFIGURATION_LOCK);
    }
}
