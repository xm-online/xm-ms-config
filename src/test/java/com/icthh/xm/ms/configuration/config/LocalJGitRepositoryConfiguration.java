package com.icthh.xm.ms.configuration.config;

import static java.util.UUID.randomUUID;

import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.repository.impl.JGitRepository;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import lombok.SneakyThrows;
import org.eclipse.jgit.api.Git;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
public class LocalJGitRepositoryConfiguration {

    @Bean
    @Primary
    @SneakyThrows
    public PersistenceConfigRepository jGitRepository(TenantContextHolder tenantContextHolder,
                                                      XmAuthenticationContextHolder authenticationContextHolder,
                                                      XmRequestContextHolder requestContextHolder) {
        File tmpDir = createTmpDir("work");
        tmpDir.mkdirs();

        ApplicationProperties.GitProperties gitProps = new ApplicationProperties.GitProperties();
        gitProps.setMaxWaitTimeSecond(30);

        final Git git = Git.init().setBare(false).setDirectory(tmpDir).call();

        return new JGitRepository(gitProps,
                                  new ReentrantLock(),
                                  tenantContextHolder,
                                  authenticationContextHolder,
                                  requestContextHolder) {
            @Override
            protected void initRepository(){}

            @Override
            protected String pull(){ return "test"; }

            @Override
            protected String commitAndPush(String commitMsg){ return "test"; }
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