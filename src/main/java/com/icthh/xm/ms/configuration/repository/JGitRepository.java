package com.icthh.xm.ms.configuration.repository;

import static com.icthh.xm.ms.configuration.security.SecurityUtils.getCurrentUserLogin;
import static com.icthh.xm.ms.configuration.utils.LockUtils.runWithLock;
import static java.io.File.separator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.*;
import static org.apache.commons.io.filefilter.TrueFileFilter.INSTANCE;
import static org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME;
import static org.eclipse.jgit.lib.RepositoryCache.FileKey.isGitRepository;

import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.config.tenant.TenantContext;
import com.icthh.xm.ms.configuration.domain.Configuration;
import com.icthh.xm.ms.configuration.utils.ReturnableTask;
import com.icthh.xm.ms.configuration.utils.Task;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;

@Slf4j
public class JGitRepository {

    private static final String GIT_FOLDER = ".git";
    private static final String GIT_COMMIT_MSG_UPDATE_TPL = "Update configuration by user [%s] from tenant [%s]";
    private static final String GIT_COMMIT_MSG_DELETE_TPL = "Delete configuration by user [%s] from tenant [%s]";

    private final GitProperties gitProperties;

    private final Lock lock;

    private final File rootDirectory;

    public JGitRepository(GitProperties gitProperties, Lock lock) {
        this.gitProperties = gitProperties;
        this.lock = lock;
        this.rootDirectory = createGitWorkDirectory();

        log.info("Git working directory {}", rootDirectory.getAbsolutePath());
        log.info("Git branch to use {}", gitProperties.getBranchName());
        initRepository();
    }

    @SneakyThrows
    public void destroy() {
        log.info("Delete git directory");
        deleteDirectory(rootDirectory);
    }

    @SneakyThrows
    private File createGitWorkDirectory() {
        return Files.createTempDirectory("xm2-config-repository").toFile();
    }

    @SneakyThrows
    protected void initRepository() {

        File repositoryFolder = rootDirectory;

        if (repositoryFolder.exists() && isGitRepository(getGitDir(), FS.DETECTED)) {
            return;
        }

        repositoryFolder.mkdirs();
        repositoryFolder.deleteOnExit();

        cloneRepository()
                .setURI(gitProperties.getUri())
                .setDirectory(repositoryFolder)
                .setCredentialsProvider(createCredentialsProvider())
                .call().close();
    }

    @SneakyThrows
    public List<Configuration> findAll() {
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            pull();
            Collection<File> files = listFiles(rootDirectory, INSTANCE, INSTANCE);
            return files.stream().filter(excludeGitFiels()).map(this::fileToConfiguration).collect(toList());
        });
    }

    private Predicate<? super File> excludeGitFiels() {
        return file -> !getRelativePath(file).startsWith(separator + GIT_FOLDER);
    }

    private String getRelativePath(File file) {
        return file.getAbsolutePath().substring(rootDirectory.getAbsolutePath().length());
    }

    @SneakyThrows
    private Configuration fileToConfiguration(File file) {
        String path = getRelativePath(file);
        String content = readFileToString(file, UTF_8);
        return new Configuration(path, content);
    }

    @SneakyThrows
    public Configuration find(String path) {
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            pull();
            String content = readFileToString(new File(getPathname(path)), UTF_8);
            return new Configuration(path, content);
        });
    }

    public void saveAll(List<Configuration> configurations) {
        log.info("Save configuration to git with path {}", configurations);
        runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_UPDATE_TPL), () -> {
            configurations.forEach(this::writeConfiguration);
        });
    }

    public void save(Configuration configuration) {
        log.info("Save configuration to git with path {}", configuration.getPath());
        runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_UPDATE_TPL), () -> {
            writeConfiguration(configuration);
        });
    }

    public void delete(String path) {
        log.info("Delete configuration from git by path {}", path);
        runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_DELETE_TPL), () -> {
            File file = new File(rootDirectory.getAbsolutePath() + path);
            if (file.exists()) {
                file.delete();
            }
        });
    }

    private String getCommitMsg(String temapate) {
        return String.format(temapate, getCurrentUserLogin(), TenantContext.getCurrent().getTenant());
    }

    private String getPathname(String path) {
        return rootDirectory.getAbsolutePath() + path;
    }

    @SneakyThrows
    private void writeConfiguration(Configuration configuration) {
        write(new File(getPathname(configuration.getPath())), configuration.getContent(), UTF_8);
    }

    private UsernamePasswordCredentialsProvider createCredentialsProvider() {
        return new UsernamePasswordCredentialsProvider(gitProperties.getLogin(), gitProperties.getPassword());
    }

    private String getGitPath() {
        return rootDirectory.getAbsolutePath() + separator + GIT_FOLDER;
    }

    private File getGitDir() {
        return new File(getGitPath());
    }

    @SneakyThrows
    protected void pull() {
        try (
                Repository db = createRepository();
                Git git = Git.wrap(db);
        ) {
            String branchName = gitProperties.getBranchName();
            try {
                git.checkout().setName(branchName).call();
                git.pull().setCredentialsProvider(createCredentialsProvider()).call();
            } catch (RefNotFoundException e) {
                log.info("Branch {} not found in local repository", branchName);
                git.fetch().setCredentialsProvider(createCredentialsProvider()).call();
                git.checkout()
                        .setCreateBranch(true)
                        .setName(branchName)
                        .setUpstreamMode(TRACK)
                        .setStartPoint(DEFAULT_REMOTE_NAME + "/" + branchName).
                        call();
                git.pull().setCredentialsProvider(createCredentialsProvider()).call();
            }
        }
    }

    @SneakyThrows
    protected void commitAndPush(String commitMsg) {
        try (
                Repository db = createRepository();
                Git git = Git.wrap(db);
        ) {
            git.add().addFilepattern(".").call();
            git.commit().setAll(true).setMessage(commitMsg).call();
            git.push().setCredentialsProvider(createCredentialsProvider()).call();
        }
    }

    private Repository createRepository() throws IOException {
        return FileRepositoryBuilder.create(getGitDir());
    }


    @SneakyThrows
    private <R, E extends Exception> R runWithPullCommit(String commitMsg, ReturnableTask<R, E> task) {
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            pull();
            R r = task.execute();
            commitAndPush(commitMsg);
            return r;
        });
    }

    private <E extends Exception> void runWithPullCommit(String commitMsg, Task<E> task) {
        runWithPullCommit(commitMsg, () -> {
            task.execute();
            return null;
        });
    }

}
