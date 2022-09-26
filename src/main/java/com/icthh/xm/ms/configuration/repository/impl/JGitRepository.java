package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.utils.LockUtils.runWithLock;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.getRequestSourceLogName;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.getRequestSourceTypeLogName;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.isRequestSourceNameExist;
import static java.io.File.separator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.write;
import static org.apache.commons.io.filefilter.TrueFileFilter.INSTANCE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK;
import static org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME;
import static org.eclipse.jgit.lib.RepositoryCache.FileKey.isGitRepository;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.config.SshTransportConfigCallback;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.utils.Task;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;
import org.springframework.util.FileSystemUtils;

@Slf4j
public class JGitRepository implements PersistenceConfigRepository {

    private static final String GIT_FOLDER = ".git";
    private static final String GIT_COMMIT_MSG_UPDATE_TPL = "Update [%s] by user [%s] from tenant [%s]. %s";
    private static final String GIT_COMMIT_MSG_DELETE_TPL = "Delete [%s] by user [%s] from tenant [%s]. %s";
    private static final String SUB_MSG_TPL_OPERATION_SRC = "Operation src [%s]";
    private static final String SUB_MSG_TPL_OPERATION_SRC_AND_APP = SUB_MSG_TPL_OPERATION_SRC + ", app name [%s]";

    private final GitProperties gitProperties;

    private final Lock lock;

    private volatile File rootDirectory;

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
        this.tenantContextHolder = tenantContextHolder;
        this.authenticationContextHolder = authenticationContextHolder;

        log.info("Git branch to use {}", gitProperties.getBranchName());
        cloneRepository();
        log.info("Git working directory {}", rootDirectory.getAbsolutePath());
    }

    @SneakyThrows
    @SuppressWarnings("unused")
    public void destroy() {
        log.info("Delete git directory: {}", rootDirectory);
        deleteDirectory(rootDirectory);
    }

    @SneakyThrows
    protected File createGitWorkDirectory() {
        return Files.createTempDirectory("xm2-config-repository").toFile();
    }

    @SneakyThrows
    protected void cloneRepository() {
        File repositoryFolder = createGitWorkDirectory();
        File oldDirectory = this.rootDirectory;
        if (oldDirectory == null) {
            this.rootDirectory = repositoryFolder;
        }

        File gitDir = getGitDir(getGitPath(repositoryFolder.getAbsolutePath()));
        if (repositoryFolder.exists() && isGitRepository(gitDir, FS.DETECTED)) {
            log.warn("Folder {} already is git folder", repositoryFolder.getAbsolutePath());
            return;
        }

        boolean mkdirsResult = repositoryFolder.mkdirs();
        if (!mkdirsResult) {
            log.warn("Cannot create dirs: {}", repositoryFolder);
        }
        repositoryFolder.deleteOnExit();

        executeLoggedAction("cloneRepository", () -> {
            CloneCommand cloneCommand = Git.cloneRepository().setURI(gitProperties.getUri())
                                                         .setDirectory(repositoryFolder);
            cloneCommand = setAuthorizationConfig(cloneCommand);
            cloneCommand.call().close();
            return null;
        });

        this.rootDirectory = repositoryFolder;
        if (oldDirectory != null) {
            oldDirectory.delete();
        }
    }

    @Override
    @SneakyThrows
    public boolean hasVersion(String version) {
        log.info("[{}] Search if commit present: {}", getRequestSourceTypeLogName(requestContextHolder), version);
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> containsGitCommit(version));
    }

    @Override
    @SneakyThrows
    public ConfigurationList findAll() {
        log.info("[{}] Find all configurations", getRequestSourceTypeLogName(requestContextHolder));
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            String commit = pull();
            List<Configuration> configurations = listFiles(rootDirectory, INSTANCE, INSTANCE)
                .stream()
                .filter(excludeGitFiles())
                .map(this::fileToConfiguration)
                .collect(toList());
            return new ConfigurationList(commit, configurations);
        });
    }

    @Override
    public void recloneConfiguration() {
        cloneRepository();
    }

    private Predicate<? super File> excludeGitFiles() {
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
    @SneakyThrows
    public ConfigurationItem find(String path, String version) {
        log.info("[{}] Find configuration by path: {} and version: {}",
                 getRequestSourceTypeLogName(requestContextHolder), path, version);

        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            if (!hasVersion(version)) {
                pull();
            }

            String content = executeGitAction("blob", git -> {
                String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
                return getBlobContent(git.getRepository(), version, normalizedPath);
            });
            return new ConfigurationItem(version, new Configuration(path, content));
        });
    }

    @SneakyThrows
    public String getBlobContent(Repository repository, String revision, String path) {

        ObjectId lastCommitId = repository.resolve(revision);
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(lastCommitId);

            RevTree tree = commit.getTree();
            TreeWalk treeWalk = new TreeWalk(repository);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(path));
            if (!treeWalk.next()) {
                return null;
            }
            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repository.open(objectId);
            byte[] content = loader.getBytes();
            revWalk.dispose();

            return new String(content, UTF_8);
        }
    }

    @Override
    public String saveAll(List<Configuration> configurations) {
        List<String> paths = configurations.stream().map(Configuration::getPath).collect(toList());

        if (!paths.isEmpty()) {
            log.info("[{}] Save configurations to git by paths {}",
                     getRequestSourceTypeLogName(requestContextHolder), paths);
            return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_UPDATE_TPL, "multiple paths"),
                                     () -> configurations.forEach(this::writeConfiguration));
        }
        log.info("[{}] configuration list is empty, nothing to save", getRequestSourceTypeLogName(requestContextHolder));
        return "undefined";

    }

    @Override
    public String setRepositoryState(List<Configuration> configurations) {
        return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_UPDATE_TPL, "multiple paths"),
                () -> {
                    deleteExistingFile("/config");
                    configurations.forEach(this::writeConfiguration);
                });
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
        return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_DELETE_TPL, paths.size()),
                                 () -> paths.forEach(this::deleteExistingFile));
    }

    @Override
    public String delete(String path) {
        log.info("[{}] Delete configuration from git by path {}",
                 getRequestSourceTypeLogName(requestContextHolder), path);
        return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_DELETE_TPL, path), () -> deleteExistingFile(path));
    }
    @Override
    public String saveOrDeleteEmpty(List<Configuration> configurations) {
        List<String> paths = configurations.stream().map(Configuration::getPath).collect(toList());

        if (!paths.isEmpty()) {
            log.info("[{}] Save or delete empty configurations to git by paths {}",
                getRequestSourceTypeLogName(requestContextHolder), paths);
            return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_UPDATE_TPL, "multiple paths and delete empty"),
                () -> configurations.forEach(it -> {
                    if (it.getContent().isEmpty()) {
                        deleteExistingFile(getPathname(it.getPath()));
                    } else {
                        writeConfiguration(it);
                    }
                }));
        }
        log.info("[{}] configuration list is empty, nothing to save", getRequestSourceTypeLogName(requestContextHolder));
        return "undefined";

    }

    private void deleteExistingFile(final String path) {
        File file = Paths.get(rootDirectory.getAbsolutePath(), path).toFile();
        if (file.isDirectory()) {
            log.info("delete whole directory by path: {}", file.getPath());
            assertDelete(FileSystemUtils.deleteRecursively(file), file.getPath());
        } else if (file.exists()) {
            assertDelete(file.delete(), file.getPath());
        } else {
            log.warn("tried to delete file which does not exists: {}", file);
        }
    }

    private void assertDelete(boolean status, String path) {
        if (!status) {
            log.error("Cannot delete file with path: {}", path);
        }
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

    private String getGitPath(String absolutePath) {
        return absolutePath + separator + GIT_FOLDER;
    }

    private File getGitDir(String gitPath) {
        return new File(gitPath);
    }

    protected String pull() {
        if (gitProperties.getCloneRepositoryOnUpdate()) {
            cloneRepository();
        }

        return executeGitAction("pull", git -> {
            String branchName = gitProperties.getBranchName();
            log.info("Start to pull branch: {}", branchName);
            try {
                git.clean().setForce(true);
                git.checkout()
                   .setName(branchName)
                   .setForce(true).call();
                PullCommand pull = git.pull();
                pull = setAuthorizationConfig(pull);
                pull.call();
                return findLastCommit(git);
            } catch (RefNotFoundException e) {
                log.info("Branch {} not found in local repository, pull from remote.", branchName);
                FetchCommand fetch = git.fetch();
                fetch = setAuthorizationConfig(fetch);
                fetch.call();
                git.checkout()
                   .setCreateBranch(true)
                   .setName(branchName)
                   .setUpstreamMode(TRACK)
                   .setStartPoint(DEFAULT_REMOTE_NAME + "/" + branchName).call();
                PullCommand pull = git.pull();
                pull = setAuthorizationConfig(pull);
                pull.call();
                return findLastCommit(git);
            }
        });
    }

    @SneakyThrows
    protected String commitAndPush(String commitMsg) {
        return executeGitAction("commitAndPush", git -> {
            if (git.status().call().isClean()) {
                log.info("Skip commit to git as working directory is clean after performing: {}", commitMsg);
                return "undefined";
            }
            git.add().addFilepattern(".").call();
            RevCommit commit = git.commit().setAll(true).setMessage(commitMsg).call();
            PushCommand push = git.push();
            push = setAuthorizationConfig(push);
            push.call();
            return commit.getName();
        });
    }

    @SneakyThrows
    private Boolean containsGitCommit(final String commit) {
        return executeGitAction("containsGitCommit", git -> {
            String branchName = gitProperties.getBranchName();
            try {
                git.clean().setForce(true);
                git.checkout().setName(branchName).setForce(true).call();
                Iterable<RevCommit> refs = git.log().call();
                // TODO there should be better way to get commit from Git like:
                //      git.log().setRevFilter(...).call()
                Optional<RevCommit> targetCommit = StreamSupport.stream(refs.spliterator(), false)
                                                                .filter(revCommit -> revCommit.getName().equals(commit))
                                                                .findFirst();
                log.info("Commit {} found in local repository", targetCommit);
                return targetCommit.isPresent();
            } catch (RefNotFoundException e) {
                log.info("Branch {} not found in local repository", branchName);
                return false;
            }
        });
    }

    private Repository createRepository() throws IOException {
        return FileRepositoryBuilder.create(getGitDir(getGitPath(rootDirectory.getAbsolutePath())));
    }

    @SneakyThrows
    private String findLastCommit(Git git) {
        Iterable<RevCommit> refs = git.log().setMaxCount(1).call();
        return StreamSupport.stream(refs.spliterator(), false)
                            .findFirst()
                            .map(AnyObjectId::getName)
                            .orElse("[N/A]");
    }

    @SneakyThrows
    private <R> R executeGitAction(String logActionName, GitFunction<R> function) {
        try (
            Repository db = createRepository();
            Git git = Git.wrap(db)
        ) {
            return executeLoggedAction(logActionName, () -> function.apply(git));
        }
    }

    @SneakyThrows
    private <R> R executeLoggedAction(String logActionName, ThrowingSupplier<R, Exception> action){
        StopWatch stopWatch = StopWatch.createStarted();
        try {
            return action.get();
        } finally {
            log.info("GIT: [{}] procedure executed in {} ms", logActionName, stopWatch.getTime());
        }
    }

    @SneakyThrows
    private <E extends Exception> String runWithPullCommit(String commitMsg, Task<E> task) {
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), () -> {
            pull();
            StopWatch stopWatch = StopWatch.createStarted();
            task.execute();
            log.info("GIT: User task executed in {} ms", stopWatch.getTime());
            return commitAndPush(commitMsg);
        });
    }

    @FunctionalInterface
    public interface GitFunction<R> {
        R apply(Git git) throws GitAPIException;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<R, E extends Exception> {
        R get() throws E;
    }

    private <T extends GitCommand> T setAuthorizationConfig(TransportCommand<T, ?> cloneCommand) {
        if (gitProperties.getSsh().isEnabled()) {
            return cloneCommand.setTransportConfigCallback(new SshTransportConfigCallback(gitProperties.getSsh()));
        }
        return cloneCommand.setCredentialsProvider(createCredentialsProvider());
    }
}
