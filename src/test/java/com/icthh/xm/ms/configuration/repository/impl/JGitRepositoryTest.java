package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.config.LocalJGitRepositoryConfiguration.createGitRepository;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.junit.Assert.assertEquals;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.request.internal.PrototypeXmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.security.internal.SpringSecurityXmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.internal.DefaultTenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import lombok.SneakyThrows;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JGitRepositoryTest {

    @Rule
    public TemporaryFolder serverGitFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder configGitFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder initTestGitFolder = new TemporaryFolder();

    private GitProperties gitProperties = new GitProperties();

    TenantContextHolder tenantContextHolder = new DefaultTenantContextHolder();
    XmAuthenticationContextHolder authenticationContextHolder = new SpringSecurityXmAuthenticationContextHolder();
    XmRequestContextHolder requestContextHolder = new PrototypeXmRequestContextHolder();

    private JGitRepository jGitRepository;

    @Before
    @SneakyThrows
    public void setUp() {
        createGitRepository(serverGitFolder, initTestGitFolder, gitProperties);

        jGitRepository = new JGitRepository(gitProperties, new ReentrantLock(),
                                            tenantContextHolder, authenticationContextHolder,
                                            requestContextHolder) {
            @Override
            @SneakyThrows
            protected File createGitWorkDirectory() {
                configGitFolder.create();
                return configGitFolder.getRoot();
            }
        };
    }



    @Test
    public void testGetByVersion() {
        String path = "/config/test.file";
        jGitRepository.save(new Configuration(path, "1"));
        jGitRepository.save(new Configuration(path, "2"));
        String ref = jGitRepository.findLastCommit();
        jGitRepository.save(new Configuration(path, "3"));
        assertEquals("3", jGitRepository.find(path).getData().getContent());
        assertEquals("2", jGitRepository.find(path, ref).getData().getContent());
    }
}
