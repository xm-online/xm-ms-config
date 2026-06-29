package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.FileProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepositoryStrategy;
import com.icthh.xm.ms.configuration.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.filefilter.TrueFileFilter.INSTANCE;

@Slf4j
public class FileRepository implements PersistenceConfigRepositoryStrategy {

    private static final ConfigVersion FILE_VERSION = new ConfigVersion("file");
    private static final int FILE_PRIORITY = 2; // between S3 (1) and GIT (Short.MAX_VALUE)

    private final Path rootDirectory;
    private final boolean readOnly;
    private final FileService fileService;

    public FileRepository(FileProperties fileProperties, FileService fileService) {
        this.rootDirectory = Path.of(fileProperties.getPath()).toAbsolutePath().normalize();
        this.readOnly = fileProperties.isReadOnly();
        this.fileService = fileService;
        log.info("File config repository root: {}, readOnly: {}", rootDirectory, readOnly);
    }

    @Override
    public String type() {
        return "FILE";
    }

    @Override
    public int priority() {
        return FILE_PRIORITY;
    }

    @Override
    public boolean isApplicable(String path) {
        return Files.isRegularFile(getAbsolutePath(path));
    }

    @Override
    public boolean hasVersion(ConfigVersion version) {
        return true;
    }

    @Override
    public ConfigurationList findAll() {
        return readFromDirectory("/config");
    }

    @Override
    public ConfigurationList findAllInTenant(String tenantKey) {
        return readFromDirectory(TENANT_PREFIX + tenantKey);
    }

    @Override
    public ConfigurationList findAllInTenants(Set<String> tenants) {
        List<Configuration> configs = tenants.stream()
            .flatMap(tenant -> readFromDirectory(TENANT_PREFIX + tenant).getData().stream())
            .collect(toList());
        return new ConfigurationList(FILE_VERSION, configs);
    }

    private ConfigurationList readFromDirectory(String relativePath) {
        File directory = getAbsolutePath(relativePath).toFile();
        if (!directory.exists()) {
            log.warn("Directory {} does not exist", directory);
            return new ConfigurationList(FILE_VERSION, List.of());
        }
        List<Configuration> configs = listFiles(directory, INSTANCE, INSTANCE).stream()
            .map(this::fileToConfiguration)
            .collect(toList());
        return new ConfigurationList(FILE_VERSION, configs);
    }

    private Configuration fileToConfiguration(File file) {
        String content = fileService.readFileToString(file.getAbsolutePath());
        return new Configuration(toConfigPath(file.toPath()), content);
    }

    @Override
    public ConfigurationItem find(String path) {
        return new ConfigurationItem(FILE_VERSION, readConfiguration(path));
    }

    @Override
    public Configuration find(String path, ConfigVersion version) {
        return readConfiguration(path);
    }

    private Configuration readConfiguration(String path) {
        Path absolute = getAbsolutePath(path);
        if (!Files.isRegularFile(absolute)) {
            return new Configuration(path, null);
        }
        return new Configuration(path, fileService.readFileToString(absolute.toString()));
    }

    @Override
    public ConfigVersion saveAll(List<Configuration> configurations, Map<String, String> configHashes) {
        assertWritable();
        configurations.forEach(this::write);
        return FILE_VERSION;
    }

    @Override
    public ConfigVersion deleteAll(List<String> paths) {
        assertWritable();
        paths.stream().filter(Objects::nonNull).forEach(this::delete);
        return FILE_VERSION;
    }

    @Override
    public ConfigVersion setRepositoryState(List<Configuration> configurations) {
        assertWritable();
        delete("/config");
        configurations.forEach(this::write);
        return FILE_VERSION;
    }

    @Override
    public void recloneConfiguration() {
        // no-op: filesystem is the source of truth; full re-read happens via refreshConfiguration
    }

    @Override
    public ConfigVersion getCurrentVersion() {
        return FILE_VERSION;
    }

    private void assertWritable() {
        if (readOnly) {
            throw new UnsupportedOperationException("FILE config repository is read-only");
        }
    }

    private void write(Configuration configuration) {
        Path absolute = getAbsolutePath(configuration.getPath());
        if (StringUtils.isEmpty(configuration.getContent())) {
            delete(configuration.getPath());
            return;
        }
        try {
            Files.createDirectories(absolute.getParent());
            Files.write(absolute, configuration.getContent().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write config file: " + absolute, e);
        }
    }

    private void delete(String path) {
        Path absolute = getAbsolutePath(path);
        try {
            File file = absolute.toFile();
            if (file.isDirectory()) {
                FileSystemUtils.deleteRecursively(file);
            } else {
                Files.deleteIfExists(absolute);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete config file: " + absolute, e);
        }
    }

    private Path getAbsolutePath(String configPath) {
        Path relative = Path.of("/", configPath).normalize();
        return Path.of(rootDirectory.toString(), relative.toString()).normalize();
    }

    private String toConfigPath(Path absolute) {
        String relative = rootDirectory.relativize(absolute.toAbsolutePath().normalize()).toString();
        return "/" + relative.replace(File.separatorChar, '/');
    }
}
