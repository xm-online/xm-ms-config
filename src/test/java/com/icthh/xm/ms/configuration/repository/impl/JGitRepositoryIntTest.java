package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.config.LocalJGitRepositoryConfiguration.createGitRepository;
import static com.icthh.xm.ms.configuration.config.LocalJGitRepositoryConfiguration.createGitRepositoryTest;
import static org.junit.Assert.assertEquals;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.request.internal.PrototypeXmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.security.internal.SpringSecurityXmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.internal.DefaultTenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.StreamSupport;

import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.service.FileService;
import lombok.SneakyThrows;
import org.eclipse.jgit.api.Git;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.core.io.ClassPathResource;

public class JGitRepositoryIntTest {

    @Rule
    public TemporaryFolder serverGitFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder configGitFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder initTestGitFolder = new TemporaryFolder();

    private final GitProperties gitProperties = new GitProperties();
    private final ApplicationProperties applicationProperties = new ApplicationProperties();
    private final FileService fileService = new FileService(applicationProperties);

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
    public void test() throws IOException {
        applicationProperties.setBinaryFileTypes(List.of(".docx"));

        String sourceFile = "simple_binary.docx";
        String targetFile = "test.docx";

        setUpRepositories(gitProperties);

        File source = new ClassPathResource(sourceFile).getFile();
        File target = configGitFolder.newFile(targetFile);
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

        String encoded = jGitRepository.find(targetFile).getData().getContent();

        byte[] bytes = Files.readAllBytes(source.toPath());
        assertEquals(Base64.getEncoder().encodeToString(bytes), encoded);
    }

    @Test
    public void testGetAll() {
        setUpRepositoriesTest(gitProperties);
        ConfigurationList list = jGitRepository.findAll();
        ConfigVersion configuration = jGitRepository.getCurrentVersion();
        assertEquals(list.getVersion().getMainVersion(), configuration.getMainVersion());
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
    public void testFindInDirectoryWhenRecloneEnabled() {
        setUpRepositories(gitProperties);
        String path1 = "/config/tenants/TENANT1/somefile";
        String path2 = "/config/tenants/TENANT2/somefile";
        jGitRepository.save(new Configuration(path1, "tenant1_content"));
        jGitRepository.save(new Configuration(path2, "tenant2_content"));

        gitProperties.setCloneRepositoryOnUpdate(true);

        ConfigurationList all = jGitRepository.findAll();
        assertEquals(2, all.getData().size());
        assertEquals("tenant1_content", all.getData().get(0).getContent());
        assertEquals("tenant2_content", all.getData().get(1).getContent());


        ConfigurationList all2 = jGitRepository.findAllInTenants(Set.of("TENANT1", "TENANT2"));
        assertEquals(2, all2.getData().size());
        assertEquals("tenant1_content", all2.getData().get(0).getContent());
        assertEquals("tenant2_content", all2.getData().get(1).getContent());
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
            requestContextHolder, fileService) {
            @Override
            @SneakyThrows
            protected File createGitWorkDirectory() {
                configGitFolder.create();
                return configGitFolder.getRoot();
            }
        };
    }

    private void setUpRepositoriesTest(GitProperties gitProps) {
        createGitRepositoryTest(serverGitFolder, initTestGitFolder, gitProps);

        jGitRepository = new JGitRepository(gitProps, new ReentrantLock(),
            tenantContextHolder, authenticationContextHolder,
            requestContextHolder, fileService) {
            @Override
            @SneakyThrows
            protected File createGitWorkDirectory() {
                configGitFolder.create();
                return configGitFolder.getRoot();
            }
        };
    }

}
