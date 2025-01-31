package com.icthh.xm.ms.configuration.config;

import static java.lang.Thread.sleep;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.JGitRepository;
import java.io.File;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PreDestroy;

import com.icthh.xm.ms.configuration.service.FileService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class LocalJGitRepositoryConfiguration {

    TemporaryFolder serverGitFolder = new TemporaryFolder();
    TemporaryFolder configGitFolder = new TemporaryFolder();
    TemporaryFolder initTestGitFolder = new TemporaryFolder();

    @Bean
    @SneakyThrows
    public PersistenceConfigRepository configRepository(ApplicationProperties applicationProperties,
                                                        TenantContextHolder tenantContextHolder,
                                                        XmAuthenticationContextHolder authenticationContextHolder,
                                                        XmRequestContextHolder requestContextHolder,
                                                        FileService fileService) {
        createGitRepository(serverGitFolder, initTestGitFolder, applicationProperties.getGit());
        ReentrantLock lock = new ReentrantLock();
        return new JGitRepository(applicationProperties.getGit(),
            lock,
            tenantContextHolder,
            authenticationContextHolder,
            requestContextHolder,
            fileService) {
            @Override
            protected void cloneRepository() {
                if (isNotBlank(applicationProperties.getGit().getUri())) {
                    super.cloneRepository();
                }
            }

            @Override
            @SneakyThrows
            protected File createGitWorkDirectory() {
                configGitFolder.create();
                return configGitFolder.getRoot();
            }
        };
    }

    @PreDestroy
    public void destory() {
        serverGitFolder.delete();
        initTestGitFolder.delete();
        configGitFolder.delete();
    }

    @SneakyThrows
    public static void createGitRepository(TemporaryFolder serverGitFolder,
                                           TemporaryFolder initTestGitFolder,
                                           GitProperties gitProperties) {
        serverGitFolder.create();
        Git git = Git.init().setBare(true).setDirectory(serverGitFolder.getRoot()).call();

        gitProperties.setUri(serverGitFolder.getRoot().getAbsolutePath());
        gitProperties.setLogin("none");
        gitProperties.setPassword("none");
        gitProperties.setBranchName("test");

        createBranch(gitProperties, initTestGitFolder);

        git.getRepository().close();
    }

    @SneakyThrows
    public static void createGitRepositoryTest(TemporaryFolder serverGitFolder,
                                           TemporaryFolder initTestGitFolder,
                                           GitProperties gitProperties) {
        serverGitFolder.create();
        Git git = Git.init().setBare(true).setDirectory(serverGitFolder.getRoot()).call();

        gitProperties.setUri(serverGitFolder.getRoot().getAbsolutePath());
        gitProperties.setLogin("none");
        gitProperties.setPassword("none");
        gitProperties.setBranchName("test");

        createBranchTest(gitProperties, initTestGitFolder);

        git.getRepository().close();
    }

    @SneakyThrows
    private static void createBranch(GitProperties gitProperties, TemporaryFolder initTestGitFolder) {
        initTestGitFolder.create();
        File root = initTestGitFolder.getRoot();
        Git.cloneRepository()
                .setURI(gitProperties.getUri())
                .setDirectory(root)
                .call().close();
        Git git = Git.open(root);
        new File(root + "/emptyFile1").createNewFile();
        git.add().addFilepattern("*").call();
        git.commit().setMessage("init commit").call();
        new File(root + "/emptyFile2").createNewFile();
        git.add().addFilepattern("*").call();
        git.commit().setMessage("second commit").call();
        git.branchRename().setNewName("test").call();
        git.push().call();
    }

    @SneakyThrows
    private static void createBranchTest(GitProperties gitProperties, TemporaryFolder initTestGitFolder) {
        initTestGitFolder.create();
        File root = initTestGitFolder.getRoot();
        Git.cloneRepository()
            .setURI(gitProperties.getUri())
            .setDirectory(root)
            .call().close();
        Git git = Git.open(root);

        new File(root + "/emptyFile1").createNewFile();
        git.add().addFilepattern("*").call();
        git.commit().setMessage("init commit").call();
        sleep(100);
        git.branchRename().setNewName("test").call();
        git.push().call();

        for (int i = 0; i < 3; i++) {
            try {
                sleep(1000);
                new File(root + "/emptyFile" + i).createNewFile();
                git.add().addFilepattern("*").call();
                git.commit().setMessage("commit" + i).call();
                git.push().call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
