package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.utils.LockUtils.runWithLock;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.getRequestSourceLogName;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.getRequestSourceTypeLogName;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.isRequestSourceNameExist;
import static java.io.File.separator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.write;
import static org.apache.commons.io.filefilter.TrueFileFilter.INSTANCE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME;
import static org.eclipse.jgit.lib.RepositoryCache.FileKey.isGitRepository;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.utils.Task;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

@Slf4j
public class JGitRepository implements PersistenceConfigRepository {

    private static final String GIT_FOLDER = ".git";
    private static final String GIT_COMMIT_MSG_UPDATE_TPL = "Update [%s] by user [%s] from tenant [%s]. %s";
    private static final String GIT_COMMIT_MSG_DELETE_TPL = "Delete [%s] by user [%s] from tenant [%s]. %s";
    private static final String SUB_MSG_TPL_OPERATION_SRC = "Operation src [%s]";
    private static final String SUB_MSG_TPL_OPERATION_SRC_AND_APP = SUB_MSG_TPL_OPERATION_SRC + ", app name [%s]";

    private final GitProperties gitProperties;

    private final Lock lock;

    private final File rootDirectory;

    private final TenantContextHolder tenantContextHolder;

    private final XmAuthenticationContextHolder authenticationContextHolder;

    private final XmRequestContextHolder requestContextHolder;

    public JGitRepository(GitProperties gitProperties,
                          Lock lock,
                          TenantContextHolder tenantContextHolder,
                          XmAuthenticationContextHolder authenticationContextHolder,
                          XmRequestContextHolder requestContextHolder) {
        this.gitProperties = gitProperties;
        this.lock = lock;
        this.requestContextHolder = requestContextHolder;
        this.rootDirectory = createGitWorkDirectory();
        this.tenantContextHolder = tenantContextHolder;
        this.authenticationContextHolder = authenticationContextHolder;

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

    @Override
    @SneakyThrows
    public boolean hasVersion(String version) {
        log.info("[{}] Search if commit present: {}", getRequestSourceTypeLogName(requestContextHolder), version);
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            try (
                Repository db = createRepository();
                Git git = Git.wrap(db);
            ) {
                String branchName = gitProperties.getBranchName();
                try {
                    git.clean().setForce(true);
                    git.checkout().setName(branchName).setForce(true).call();
                    Iterable<RevCommit> refs = git.log().call();
                    Optional<RevCommit> targetCommit = StreamSupport.stream(refs.spliterator(), false)
                        .filter(revCommit -> revCommit.getName().equals(version))
                        .findFirst();
                    log.info("Commit {} found in local repository", targetCommit);
                    return targetCommit.isPresent();
                } catch (RefNotFoundException e) {
                    log.info("Branch {} not found in local repository", branchName);
                    return false;
                }
            }
        });
    }

    @Override
    @SneakyThrows
    public ConfigurationList findAll() {
        log.info("[{}] Find all configurations", getRequestSourceTypeLogName(requestContextHolder));
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            String commit = pull();
            Collection<File> files = listFiles(rootDirectory, INSTANCE, INSTANCE);
            return new ConfigurationList(commit, files.stream().filter(excludeGitFiels()).map(file -> fileToConfiguration(file)).collect(toList()));
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
        String content = readFileToString(file, UTF_8);
        String path = StringUtils.replaceChars(getRelativePath(file), File.separator, "/");
        return new Configuration(path, content);
    }

    @Override
    @SneakyThrows
    public ConfigurationItem find(String path) {
        log.info("[{}] Find configuration by path: {}", getRequestSourceTypeLogName(requestContextHolder), path);
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            String commit = pull();
            String content = readFileToString(new File(getPathname(path)), UTF_8);
            return new ConfigurationItem(commit, new Configuration(path, content));
        });
    }

    @Override
    public String saveAll(List<Configuration> configurations) {
        List<String> paths = configurations.stream().map(Configuration::getPath).collect(toList());

        log.info("[{}] Save configurations to git by paths {}",
                 getRequestSourceTypeLogName(requestContextHolder), paths);
        return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_UPDATE_TPL, "multiple paths"),
                          () -> configurations.forEach(this::writeConfiguration));
    }

    @Override
    public String save(Configuration configuration) {
        return save(configuration, null);
    }

    @Override
    public String save(Configuration configuration, String oldConfigHash) {
        log.info("[src: {}] Save configuration to git with path {}", getRequestSourceTypeLogName(requestContextHolder),
                 configuration.getPath());
        return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_UPDATE_TPL, configuration.getPath()), () -> {
            assertConfigHash(configuration, oldConfigHash);
            writeConfiguration(configuration);
        });
    }

    @SneakyThrows
    private void assertConfigHash(Configuration configuration, String oldConfigHash) {
        if (isBlank(oldConfigHash)) {
            return;
        }

        String path = configuration.getPath();
        String content = readFileToString(new File(getPathname(path)), UTF_8);
        String expectedOldConfigHash = sha1Hex(content);
        log.info("Expected hash {}, actual hash {}", expectedOldConfigHash, oldConfigHash);
        if (!expectedOldConfigHash.equals(oldConfigHash)) {
            throw new ConcurrentConfigModificationException();
        }
    }

    @Override
    public String deleteAll(List<String> paths) {
        log.info("[{}] Delete configurations from git by paths {}",
            getRequestSourceTypeLogName(requestContextHolder), paths);
        return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_DELETE_TPL, paths.size()), () -> {
            paths.forEach(path -> {
                File file = new File(rootDirectory.getAbsolutePath() + path);
                if (file.exists()) {
                    file.delete();
                }
            });
        });
    }

    @Override
    public String delete(String path) {
        log.info("[{}] Delete configuration from git by path {}",
                 getRequestSourceTypeLogName(requestContextHolder), path);
        return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_DELETE_TPL, path), () -> {
            File file = new File(rootDirectory.getAbsolutePath() + path);
            if (file.exists()) {
                file.delete();
            }
        });
    }

    private String getCommitMsg(String template, Object path) {
        String operationSourceMsg;
        if (isRequestSourceNameExist(requestContextHolder)) {
            operationSourceMsg = String.format(SUB_MSG_TPL_OPERATION_SRC_AND_APP,
                                               getRequestSourceTypeLogName(requestContextHolder),
                                               getRequestSourceLogName(requestContextHolder));
        } else {
            operationSourceMsg = String.format(SUB_MSG_TPL_OPERATION_SRC,
                                               getRequestSourceTypeLogName(requestContextHolder));
        }

        return String.format(template,
                             path,
                             authenticationContextHolder.getContext().getLogin().orElse("unknown"),
                             TenantContextUtils.getTenantKey(tenantContextHolder).map(TenantKey::getValue).orElse("no tenant"),
                             operationSourceMsg);
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
    protected String pull() {
        try (
            Repository db = createRepository();
            Git git = Git.wrap(db);
        ) {
            String branchName = gitProperties.getBranchName();
            try {
                git.clean().setForce(true);
                git.checkout().setName(branchName).setForce(true).call();
                git.pull().setCredentialsProvider(createCredentialsProvider()).call();
                Iterable<RevCommit> refs = git.log().call();
                RevCommit lastCommit = StreamSupport.stream(refs.spliterator(), false)
                    .max(comparing(RevCommit::getCommitTime))
                    .get();
                return lastCommit.getName();
            } catch (RefNotFoundException e) {
                log.info("Branch {} not found in local repository", branchName);
                git.fetch().setCredentialsProvider(createCredentialsProvider()).call();
                git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .setUpstreamMode(TRACK)
                    .setStartPoint(DEFAULT_REMOTE_NAME + "/" + branchName)
                    .call();
                git.pull().setCredentialsProvider(createCredentialsProvider()).call();
                Iterable<RevCommit> refs = git.log().call();
                RevCommit lastCommit = StreamSupport.stream(refs.spliterator(), false)
                    .max(comparing(RevCommit::getCommitTime))
                    .get();
                return lastCommit.getName();
            }
        }
    }

    @SneakyThrows
    protected String commitAndPush(String commitMsg) {
        try (
            Repository db = createRepository();
            Git git = Git.wrap(db);
        ) {
            git.add().addFilepattern(".").call();
            RevCommit commit = git.commit().setAll(true).setMessage(commitMsg).call();
            git.push().setCredentialsProvider(createCredentialsProvider()).call();
            return commit.getName();
        }
    }

    private Repository createRepository() throws IOException {
        return FileRepositoryBuilder.create(getGitDir());
    }


    @SneakyThrows
    private <E extends Exception> String runWithPullCommit(String commitMsg, Task<E> task) {
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            pull();
            task.execute();
            return commitAndPush(commitMsg);
        });
    }
}
