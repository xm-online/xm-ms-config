package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;
import static com.icthh.xm.ms.configuration.utils.FileUtils.readFileToString;
import static com.icthh.xm.ms.configuration.utils.LockUtils.runWithLock;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.getRequestSourceLogName;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.getRequestSourceTypeLogName;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.isRequestSourceNameExist;
import static java.io.File.separator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FileUtils.write;
import static org.apache.commons.io.filefilter.TrueFileFilter.INSTANCE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK;
import static org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME;
import static org.eclipse.jgit.lib.RepositoryCache.FileKey.isGitRepository;
import static org.springframework.util.FileSystemUtils.deleteRecursively;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.GitProperties;
import com.icthh.xm.ms.configuration.config.SshTransportConfigCallback;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.utils.Task;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
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
    public static final String UNDEFINED_COMMIT = "undefined";
    public static final String REFS_HEADS = "refs/heads/";
    public static final String GIT_REPOSITORY = "git repository";

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

    @Override
    public boolean hasVersion(ConfigVersion version) {
        log.info("[{}] Search if commit present: {}", getRequestSourceTypeLogName(requestContextHolder), version);
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), GIT_REPOSITORY, () -> containsGitCommit(version.getMainVersion()));
    }

    @Override
    public ConfigurationList findAll() {
        return readFromDirectory("/config");
    }

    @Override
    public ConfigurationList findAllInTenant(String tenantKey) {
        return readFromDirectory(TENANT_PREFIX + tenantKey);
    }

    private ConfigurationList readFromDirectory(String relativePath) {
        File rootDirectory = this.rootDirectory;
        File directory = Paths.get(rootDirectory.getAbsolutePath(), relativePath).toFile();
        return readConfigsFromDirectories(List.of(directory));
    }

    @Override
    public ConfigurationList findAllInTenants(Set<String> tenants) {
        log.info("[{}] Find configurations in tenants {}", getRequestSourceTypeLogName(requestContextHolder), tenants);
        List<File> directories = tenants.stream()
            .map(tenant -> Paths.get(rootDirectory.getAbsolutePath(), TENANT_PREFIX, tenant).toFile())
            .collect(toList());
        return readConfigsFromDirectories(directories);
    }

    @Override
    public ConfigurationItem find(String path) {
        log.info("[{}] Find configuration by path: {}", getRequestSourceTypeLogName(requestContextHolder), path);
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), GIT_REPOSITORY, () -> {
            String commit = pull();
            String content = readFileToString(getAbsolutePath(path));
            return new ConfigurationItem(new ConfigVersion(commit), new Configuration(path, content));
        });
    }

    @Override
    public Configuration find(String path, ConfigVersion version) {
        log.info("[{}] Find configuration by path: {} and version: {}",
            getRequestSourceTypeLogName(requestContextHolder), path, version);

        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), GIT_REPOSITORY, () -> {
            if (!hasVersion(version)) {
                pull();
            }

            String content = executeGitAction("blob", git -> {
                String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
                return getBlobContent(git.getRepository(), version.getMainVersion(), normalizedPath);
            });
            return new Configuration(path, content);
        });
    }

    @Override
    public ConfigVersion recloneConfiguration() {
        cloneRepository();
        return getCurrentVersion();
    }

    @Override
    public ConfigVersion saveAll(List<Configuration> configurations, Map<String, String> configHashes) {
        List<String> paths = configurations.stream().map(Configuration::getPath).collect(toList());
        Map<String, String> hashes = configHashes == null ? Map.of() : configHashes;

        if (!paths.isEmpty()) {
            log.info("[{}] Save configurations to git by paths {}",
                getRequestSourceTypeLogName(requestContextHolder), paths);
            return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_UPDATE_TPL, "multiple paths"),
                () -> configurations.forEach(it -> save(it, hashes.get(it.getPath()))));
        }
        log.info("[{}] configuration list is empty, nothing to save", getRequestSourceTypeLogName(requestContextHolder));
        return getCurrentVersion();
    }

    @Override
    public ConfigVersion setRepositoryState(List<Configuration> configurations) {
        return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_UPDATE_TPL, "multiple paths"),
            () -> {
                deleteExistingFile("/config");
                configurations.forEach(this::writeConfiguration);
            });
    }


    @Override
    public ConfigVersion deleteAll(List<String> paths) {
        log.info("[{}] Delete configurations from git by paths {}",
            getRequestSourceTypeLogName(requestContextHolder), paths);
        return runWithPullCommit(getCommitMsg(GIT_COMMIT_MSG_DELETE_TPL, paths.size()),
            () -> paths.forEach(this::deleteExistingFile));
    }

    @Override
    public ConfigVersion getCurrentVersion() {
        return new ConfigVersion(executeGitAction("getCurrentVersion", this::findLastCommit));
    }

    @SneakyThrows
    protected File createGitWorkDirectory() {
        return Files.createTempDirectory("xm2-config-repository").toFile();
    }

    protected void cloneRepository() {
        log.debug("Clone git repository");
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
            cloneCommand.setBranchesToClone(List.of(REFS_HEADS + gitProperties.getBranchName()));
            cloneCommand.setCloneAllBranches(false);
            cloneCommand.setCloneSubmodules(false);
            cloneCommand.setBranch(gitProperties.getBranchName());
            cloneCommand.setProgressMonitor(NullProgressMonitor.INSTANCE);
            if (gitProperties.getDepth() > 0) {
                cloneCommand.setDepth(gitProperties.getDepth());
            }
            cloneCommand.call().close();
            return null;
        });

        this.rootDirectory = repositoryFolder;
        if (oldDirectory != null) {
            deleteRecursively(oldDirectory);
        }
    }

    @SuppressWarnings("unused")
    public void destroy() {
        log.info("Delete git directory: {}", rootDirectory);
        deleteRecursively(rootDirectory);
    }

    private ConfigurationList readConfigsFromDirectories(List<File> directories) {
        var paths = directories.stream().map(this::getRelativePath).collect(toList());
        log.info("[{}] Find configurations in directory {}", getRequestSourceTypeLogName(requestContextHolder), paths);
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), GIT_REPOSITORY, () -> {
            String commit = pull();
            List<Configuration> configurations = new ArrayList<>();
            directories.forEach(directory -> configurations.addAll(internalReadFileSystemFolder(directory)));
            return new ConfigurationList(new ConfigVersion(commit), configurations);
        });
    }

    private List<Configuration> internalReadFileSystemFolder(File directory) {
        if (!directory.exists()) {
            log.warn("Directory {} does not exist", directory);
            return List.of();
        }

        return listFiles(directory, INSTANCE, INSTANCE)
            .stream()
            .filter(excludeGitFiles())
            .map(this::fileToConfiguration)
            .collect(toList());
    }

    private Predicate<? super File> excludeGitFiles() {
        return file -> !getRelativePath(file).startsWith(separator + GIT_FOLDER);
    }

    private String getRelativePath(File file) {
        return file.getAbsolutePath().substring(rootDirectory.getAbsolutePath().length());
    }

    private Configuration fileToConfiguration(File file) {
        String content = readFileToString(file.getAbsolutePath());
        String path = StringUtils.replaceChars(getRelativePath(file), File.separator, "/");
        return new Configuration(path, content);
    }

    @SneakyThrows
    private String getBlobContent(Repository repository, String revision, String path) {

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

    private void save(Configuration configuration, String oldConfigHash) {
        assertConfigHash(configuration, oldConfigHash);
        if (StringUtils.isEmpty(configuration.getContent())) {
            deleteExistingFile(configuration.getPath());
        } else {
            writeConfiguration(configuration);
        }
    }

    @SneakyThrows
    private void assertConfigHash(Configuration configuration, String oldConfigHash) {
        if (isBlank(oldConfigHash)) {
            return;
        }

        String path = configuration.getPath();
        String content = readFileToString(getAbsolutePath(path));
        String expectedOldConfigHash = sha1Hex(content);
        log.debug("Expected hash {}, actual hash {}", expectedOldConfigHash, oldConfigHash);
        if (!expectedOldConfigHash.equals(oldConfigHash)) {
            throw new ConcurrentConfigModificationException();
        }
    }

    private void deleteExistingFile(final String path) {
        File file = Paths.get(getAbsolutePath(path)).toFile();
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

    private String getAbsolutePath(String path) {
        return rootDirectory.getAbsolutePath() + Path.of("/", path).normalize();
    }

    @SneakyThrows
    private void writeConfiguration(Configuration configuration) {
        write(new File(getAbsolutePath(configuration.getPath())), configuration.getContent(), UTF_8);
    }

    private UsernamePasswordCredentialsProvider createCredentialsProvider() {
        String password = gitProperties.getPassword();
        String login = gitProperties.getLogin();
        login = login == null ? "" : login;
        password = password == null ? "" : password;
        return new UsernamePasswordCredentialsProvider(login, password);
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
                checkout(git);
                PullCommand pull = git.pull();
                pull = setAuthorizationConfig(pull);
                pull.setRebase(false);
                pull.setProgressMonitor(NullProgressMonitor.INSTANCE);

                pull.call();
                return findLastCommit(git);
            } catch (RefNotFoundException e) {
                log.info("Branch {} not found in local repository, pull from remote.", branchName);
                FetchCommand fetch = git.fetch();
                fetch = setAuthorizationConfig(fetch);
                if (gitProperties.getDepth() > 0) {
                    fetch.setDepth(gitProperties.getDepth());
                }
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
    private void checkout(Git git) throws RefNotFoundException {
        String branchName = gitProperties.getBranchName();
        if (!branchName.equals(git.getRepository().getBranch())) {
            git.clean().setForce(true).call();
            git.checkout()
                .setName(branchName)
                .setCreateBranch(false)
                .setProgressMonitor(NullProgressMonitor.INSTANCE)
                .setForceRefUpdate(true).call();
        }
    }

    @SneakyThrows
    protected String commitAndPush(String commitMsg) {
        return executeGitAction("commitAndPush", git -> {
            StatusCommand statusCommand = git.status();
            statusCommand.setProgressMonitor(NullProgressMonitor.INSTANCE);
            Status status = statusCommand.call();
            String lastCommit = findLastCommit(git);
            if (status.isClean()) {
                log.info("Skip commit to git as working directory is clean after performing: {}, lastCommit: {}", commitMsg, lastCommit);
                return lastCommit;
            }

            String branchName = gitProperties.getBranchName();

            Set<String> filePatterns = new HashSet<>();
            filePatterns.addAll(status.getUntracked());
            filePatterns.addAll(status.getUncommittedChanges());
            if (filePatterns.isEmpty()) {
                log.info("Skip commit to git as no file changed after performing: {}, lastCommit: {}", commitMsg, lastCommit);
                return lastCommit;
            }

            AddCommand addCmd = git.add();
            for (String file : filePatterns) {
                addCmd.addFilepattern(file);
            }
            addCmd.call();

            RevCommit commit = git.commit().setMessage(commitMsg).call();

            PushCommand push = git.push();
            push = setAuthorizationConfig(push);
            push.setThin(true);
            push.setProgressMonitor(NullProgressMonitor.INSTANCE);
            if (isNotBlank(branchName)) {
                push.setRefSpecs(new RefSpec(REFS_HEADS + branchName));
            }
            push.call();

            return commit.getName();
        });
    }

    @SneakyThrows
    private void disablePreloadIndexAndFileMode(Git git) {
        StoredConfig config = git.getRepository().getConfig();
        config.setBoolean("core", null, "filemode", false);
        config.setBoolean("core", null, "preloadindex", false);
        config.save();
    }

    @SneakyThrows
    private Boolean containsGitCommit(final String commit) {
        return executeGitAction("containsGitCommit", git -> {
            String branchName = gitProperties.getBranchName();
            try {
                checkout(git);

                ObjectId jCommit = git.getRepository().resolve(commit);
                if (jCommit == null) {
                    log.warn("Could not find commit: {} due to wrong revision format", commit);
                    return false;
                }

                try (RevWalk revWalk = new RevWalk(git.getRepository())) {
                    RevCommit revCommit = revWalk.parseCommit(jCommit);
                    if (revCommit != null) {
                        log.info("Successfully found commit: {} in the local repository", commit);
                        return true;
                    } else {
                        log.info("Could not find commit: {} in the local repository", commit);
                        return false;
                    }
                }
            } catch (MissingObjectException e) {
                log.info("Could not find commit: {} due to missing in the local repo", commit);
                return false;
            } catch (RefNotFoundException e) {
                log.warn("Branch {} not found in local repository", branchName);
                return false;
            } catch (IOException e) {
                log.warn("Could not find commit: {} due to unexpected exception", commit, e);
                return false;
            }
        });
    }

    @SneakyThrows
    private Repository createRepository() {
        return FileRepositoryBuilder.create(getGitDir(getGitPath(rootDirectory.getAbsolutePath())));
    }

    @SneakyThrows
    private String findLastCommit(Git git) {
        File directory = git.getRepository().getDirectory();
        if (directory != null && directory.exists()) {
            log.debug("Find last commit for local repository: {}", directory.getAbsolutePath());
        }

        log.debug("Using the new style of getting the last commit");
        RevWalk walk = new RevWalk(git.getRepository());

        walk.markStart(walk.parseCommit(git.getRepository().resolve(Constants.HEAD)));
        walk.sort(RevSort.COMMIT_TIME_DESC, true );
        Optional<RevCommit> lastCommit = Optional.ofNullable(walk.iterator().next());

        if (lastCommit.isPresent()) {
            RevCommit revCommit = lastCommit.get();
            log.debug("Commit {} found in local repository", revCommit.getName());
            log.debug("Commit {}, time: {}", revCommit.getName(), revCommit.getCommitTime());
            log.debug("Commit {}, message: {}", revCommit.getName(), revCommit.getFullMessage());
        }

        return lastCommit.map(AnyObjectId::getName).orElse("[N/A]");
    }

    private <R> R executeGitAction(String logActionName, GitFunction<R> function) {
        try (
            Repository db = createRepository();
            Git git = Git.wrap(db)
        ) {
            disablePreloadIndexAndFileMode(git);
            return executeLoggedAction(logActionName, () -> function.apply(git));
        }
    }

    @SneakyThrows
    private <R> R executeLoggedAction(String logActionName, ThrowingSupplier<R, Exception> action) {
        StopWatch stopWatch = StopWatch.createStarted();
        try {
            return action.get();
        } finally {
            log.info("GIT: [{}] procedure executed in {} ms", logActionName, stopWatch.getTime());
        }
    }

    private <E extends Exception> ConfigVersion runWithPullCommit(String commitMsg, Task<E> task) {
        return runWithLock(lock, gitProperties.getMaxWaitTimeSecond(), GIT_REPOSITORY, () -> {
            pull();
            StopWatch stopWatch = StopWatch.createStarted();
            task.execute();
            log.info("GIT: User task executed in {} ms", stopWatch.getTime());
            String commit = commitAndPush(commitMsg);
            return new ConfigVersion(commit);
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

    private <T extends GitCommand<?>> T setAuthorizationConfig(TransportCommand<T, ?> cloneCommand) {
        if (gitProperties.getSsh().isEnabled()) {
            return cloneCommand.setTransportConfigCallback(new SshTransportConfigCallback(gitProperties.getSsh()));
        }
        return cloneCommand.setCredentialsProvider(createCredentialsProvider());
    }
}
