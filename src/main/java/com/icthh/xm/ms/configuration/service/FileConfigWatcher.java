package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.FileProperties;
import com.icthh.xm.ms.configuration.repository.impl.FileRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Watches the FILE-mode config folder via {@link FileAlterationObserver} and pushes changes to
 * {@link ConfigurationService}. Filesystem events are accumulated into a map; every debounce-time
 * seconds the map is swapped, the changed files are read from the repository and handed to
 * {@link ConfigurationService#updateConfigurationInMemory(List)} (deleted files are sent as empty
 * content, which removes them). Writes made through the repository are caught here the same way as
 * any other filesystem change.
 */
@Slf4j
public class FileConfigWatcher {

    private final Path rootDirectory;
    private final ConfigurationService configurationService;
    private final FileRepository fileRepository;
    private final FileAlterationObserver observer;

    private volatile Map<String, Boolean> changes = new ConcurrentHashMap<>();

    public FileConfigWatcher(FileProperties fileProperties,
                             ConfigurationService configurationService,
                             FileRepository fileRepository) {
        this.rootDirectory = Path.of(fileProperties.getPath()).toAbsolutePath().normalize();
        this.configurationService = configurationService;
        this.fileRepository = fileRepository;
        this.observer = buildObserver(rootDirectory.resolve("config"));
    }

    private FileAlterationObserver buildObserver(Path configDirectory) {
        try {
            FileAlterationObserver fileObserver = FileAlterationObserver.builder().setFile(configDirectory.toFile()).get();
            fileObserver.addListener(new FileAlterationListenerAdaptor() {
                @Override
                public void onFileCreate(File file) {
                    changes.put(file.getAbsolutePath(), true);
                }

                @Override
                public void onFileChange(File file) {
                    changes.put(file.getAbsolutePath(), true);
                }

                @Override
                public void onFileDelete(File file) {
                    changes.put(file.getAbsolutePath(), true);
                }
            });
            return fileObserver;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create file observer for " + configDirectory, e);
        }
    }

    @PostConstruct
    void initialize() throws Exception {
        observer.initialize(); // baseline snapshot of current files
        log.info("File config watcher initialized on {}", rootDirectory.resolve("config"));
    }

    @PreDestroy
    void destroy() {
        try {
            observer.destroy();
        } catch (Exception e) {
            log.warn("Error destroying file observer", e);
        }
    }

    @Scheduled(fixedDelayString = "${application.config-repository.file.debounce-time:2}", timeUnit = TimeUnit.SECONDS)
    public void flushChanges() {
        observer.checkAndNotify(); // diff the tree, fire listener -> fill `changes`
        if (changes.isEmpty()) {
            return;
        }
        Map<String, Boolean> batch = changes;    // store reference
        changes = new ConcurrentHashMap<>();     // replace volatile reference
        List<Configuration> configurations = batch.keySet().stream()
            .map(this::toConfigPath)
            .map(this::read)
            .collect(Collectors.toList());
        configurationService.updateConfigurationInMemory(configurations);
    }

    /** Reads the current content of a changed path; a deleted file is read as empty content (removal). */
    private Configuration read(String configPath) {
        String content = fileRepository.find(configPath).getData().getContent();
        return new Configuration(configPath, content == null ? "" : content);
    }

    private String toConfigPath(String absolutePath) {
        String relative = rootDirectory.relativize(Path.of(absolutePath).normalize()).toString();
        return "/" + relative.replace(File.separatorChar, '/');
    }
}
