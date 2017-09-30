package com.icthh.xm.ms.configuration.config;

import static java.util.Collections.emptyList;

import com.hazelcast.core.HazelcastInstance;
import com.icthh.xm.ms.configuration.repository.JGitRepository;
import lombok.SneakyThrows;
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
    public JGitRepository jGitRepository(ApplicationProperties applicationProperties, HazelcastInstance hazelcastInstance) {

        return new JGitRepository(applicationProperties.getGit(), new ReentrantLock()) {
            @Override
            protected void initRepository(){};
            @Override
            protected void pull(){};
            @Override
            protected void commitAndPush(String commitMsg){};
            @Override
            public List<com.icthh.xm.ms.configuration.domain.Configuration> findAll(){
                return emptyList();
            }
        };
    }


}
