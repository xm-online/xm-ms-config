package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractUnitTest;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.FileProperties;
import com.icthh.xm.ms.configuration.repository.impl.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class FileConfigWatcherUnitTest extends AbstractUnitTest {

    @TempDir
    Path tempDir;

    private Path configDir;
    private ConfigurationService service;
    private FileConfigWatcher watcher;

    @BeforeEach
    void setUp() throws Exception {
        configDir = tempDir.resolve("config");
        Files.createDirectories(configDir.resolve("tenants/XM"));
        Files.writeString(configDir.resolve("tenants/XM/a.yml"), "v1");
        Files.writeString(configDir.resolve("tenants/XM/b.yml"), "v1");

        FileProperties props = new FileProperties();
        props.setPath(tempDir.toString());
        FileRepository fileRepository = new FileRepository(props, new FileService(new ApplicationProperties()));
        service = mock(ConfigurationService.class);
        watcher = new FileConfigWatcher(props, service, fileRepository);
        watcher.initialize(); // baseline snapshot includes a.yml, b.yml
    }

    @SuppressWarnings("unchecked")
    private List<Configuration> flushAndCapture() {
        watcher.flushChanges();
        ArgumentCaptor<List<Configuration>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).updateConfigurationInMemory(captor.capture());
        return captor.getValue();
    }

    private static Set<String> paths(List<Configuration> configs) {
        return configs.stream().map(Configuration::getPath).collect(Collectors.toSet());
    }

    @Test
    void modifiedFile_isReportedWithContent() throws IOException {
        Files.writeString(configDir.resolve("tenants/XM/a.yml"), "version-2"); // different length -> always detected
        List<Configuration> configs = flushAndCapture();

        assertThat(paths(configs)).contains("/config/tenants/XM/a.yml");
        Configuration changed = configs.stream()
            .filter(c -> c.getPath().equals("/config/tenants/XM/a.yml")).findFirst().orElseThrow();
        assertThat(changed.getContent()).isEqualTo("version-2");
    }

    @Test
    void newFileInNewDirectory_isReported() throws IOException {
        Files.createDirectories(configDir.resolve("tenants/YY"));
        Files.writeString(configDir.resolve("tenants/YY/new.yml"), "hello");
        assertThat(paths(flushAndCapture())).contains("/config/tenants/YY/new.yml");
    }

    @Test
    void deletedFile_isReportedAsEmptyContent() throws IOException {
        Files.delete(configDir.resolve("tenants/XM/b.yml"));
        List<Configuration> configs = flushAndCapture();

        Configuration deleted = configs.stream()
            .filter(c -> c.getPath().equals("/config/tenants/XM/b.yml")).findFirst().orElseThrow();
        assertThat(deleted.getContent()).isEmpty();
    }
}
