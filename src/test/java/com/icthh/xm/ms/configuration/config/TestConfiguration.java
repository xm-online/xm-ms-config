package com.icthh.xm.ms.configuration.config;

import static java.util.Collections.emptyList;

import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.domain.Configurations;
import com.icthh.xm.ms.configuration.repository.impl.JGitRepository;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import lombok.SneakyThrows;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
public class TestConfiguration {

    @Bean
    @Primary
    @SneakyThrows
    public PersistenceConfigRepository jGitRepository(ApplicationProperties applicationProperties,
                                                      TenantContextHolder tenantContextHolder,
                                                      XmAuthenticationContextHolder authenticationContextHolder,
                                                      XmRequestContextHolder requestContextHolder) {

        return new JGitRepository(applicationProperties.getGit(), new ReentrantLock(),
                                  tenantContextHolder, authenticationContextHolder, requestContextHolder) {
            @Override
            protected void initRepository(){}

            @Override
            protected String pull(){ return "test";}

            @Override
            protected String commitAndPush(String commitMsg){ return "test";}

            @Override
            public Configurations findAll(){
                return new Configurations("test", emptyList());
            }
        };
    }


}
