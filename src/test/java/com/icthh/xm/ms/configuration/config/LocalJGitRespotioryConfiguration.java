package com.icthh.xm.ms.configuration.config;

import static java.util.UUID.randomUUID;

import com.hazelcast.core.HazelcastInstance;
import com.icthh.xm.ms.configuration.repository.JGitRepository;
import lombok.SneakyThrows;
import org.eclipse.jgit.api.Git;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
public class LocalJGitRespotioryConfiguration {

    @Bean
    @Primary
    @SneakyThrows
    public JGitRepository jGitRepository(ApplicationProperties applicationProperties, HazelcastInstance hazelcastInstance) {
        File tmpDir = createTmpDir("work");
        tmpDir.mkdirs();

        ApplicationProperties.GitProperties gitProps = new ApplicationProperties.GitProperties();
        gitProps.setMaxWaitTimeSecond(30);

        final Git git = Git.init().setBare(false).setDirectory(tmpDir).call();

        return new JGitRepository(gitProps, new ReentrantLock()) {
            @Override
            protected void initRepository(){};
            @Override
            protected void pull(){};
            @Override
            protected void commitAndPush(String commitMsg){};
        };
    }

    private File createTmpDir(String name) throws IOException {
        File file = File.createTempFile("jgit_test_" + randomUUID().toString(), name, null);
        if (!file.delete()) {
            throw new IOException("Cannot obtain unique path");
        }
        return file;
    }

}