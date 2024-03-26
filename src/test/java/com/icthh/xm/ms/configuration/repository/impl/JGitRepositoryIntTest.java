package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.config.LocalJGitRepositoryConfiguration.createGitRepository;
import static org.junit.Assert.assertEquals;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.request.internal.PrototypeXmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.security.internal.SpringSecurityXmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.internal.DefaultTenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import java.io.File;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import org.eclipse.jgit.api.Git;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JGitRepositoryIntTest {

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

    @Test
    public void testGetByVersion() {
        setUpRepositories(gitProperties);
        String path = "/config/test.file";
        jGitRepository.save(new Configuration(path, "1"));
        ConfigVersion ref = jGitRepository.save(new Configuration(path, "2"));
        jGitRepository.save(new Configuration(path, "3"));
        assertEquals("3", jGitRepository.find(path).getData().getContent());
        assertEquals("2", jGitRepository.find(path, ref).getContent());
    }

    @Test
    public void testSave_shouldReturnLastCommitWhenNoFilesChanged() {
        setUpRepositories(gitProperties);
        String path = "/config/dummy";
        ConfigVersion commit1 = jGitRepository.save(new Configuration(path, "unchanged_content"));
        ConfigVersion commit2 = jGitRepository.save(new Configuration(path, "unchanged_content"));
        assertEquals("Expected then save return last commit when no files changed", commit1,
            commit2);
    }

    @Test
    public void testDepth_shouldCloneAllCommitsWhenDepthMinusOne() {
        setUpRepositories(gitProperties);
        try (Git git = Git.open(configGitFolder.getRoot())) {
            long commitsCount = StreamSupport.stream(git.log().call().spliterator(), false).count();
            assertEquals(2, commitsCount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testDepth_shouldCloneOneCommitWhenDepthOne() {
        gitProperties.setDepth(1);
        setUpRepositories(gitProperties);
        try (Git git = Git.open(configGitFolder.getRoot())) {
            long commitsCount = StreamSupport.stream(git.log().call().spliterator(), false).count();
            assertEquals(1, commitsCount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setUpRepositories(GitProperties gitProps) {
        createGitRepository(serverGitFolder, initTestGitFolder, gitProps);

        jGitRepository = new JGitRepository(gitProps, new ReentrantLock(),
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

}
