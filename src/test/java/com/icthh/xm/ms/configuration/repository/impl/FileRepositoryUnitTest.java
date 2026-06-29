package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractUnitTest;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.FileProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FileRepositoryUnitTest extends AbstractUnitTest {

    @TempDir
    Path tempDir;

    private FileRepository repository(boolean readOnly, ApplicationProperties appProps) throws IOException {
        Files.createDirectories(tempDir.resolve("config/tenants/XM"));
        FileProperties props = new FileProperties();
        props.setPath(tempDir.toString());
        props.setReadOnly(readOnly);
        FileService fileService = new FileService(appProps);
        return new FileRepository(props, fileService);
    }

    private FileRepository repository(boolean readOnly) throws IOException {
        return repository(readOnly, new ApplicationProperties());
    }

    @Test
    public void type_isFILE() throws IOException {
        assertThat(repository(false).type()).isEqualTo("FILE");
    }

    @Test
    public void findAll_readsFilesAndMapsConfigPaths() throws IOException {
        FileRepository repo = repository(false);
        Files.writeString(tempDir.resolve("config/tenants/XM/a.yml"), "content-a");

        List<Configuration> data = repo.findAll().getData();

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getPath()).isEqualTo("/config/tenants/XM/a.yml");
        assertThat(data.get(0).getContent()).isEqualTo("content-a");
    }

    @Test
    public void findAllInTenant_readsOnlyThatTenant() throws IOException {
        FileRepository repo = repository(false);
        Files.createDirectories(tempDir.resolve("config/tenants/OTHER"));
        Files.writeString(tempDir.resolve("config/tenants/XM/a.yml"), "xm");
        Files.writeString(tempDir.resolve("config/tenants/OTHER/b.yml"), "other");

        List<Configuration> data = repo.findAllInTenant("XM").getData();

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getPath()).isEqualTo("/config/tenants/XM/a.yml");
    }

    @Test
    public void find_byMissingPath_returnsNullContent() throws IOException {
        FileRepository repo = repository(false);
        Configuration result = repo.find("/config/tenants/XM/missing.yml", new ConfigVersion("file"));
        assertThat(result.getContent()).isNull();
    }

    @Test
    public void isApplicable_trueOnlyForExistingFile() throws IOException {
        FileRepository repo = repository(false);
        Files.writeString(tempDir.resolve("config/tenants/XM/a.yml"), "x");

        assertThat(repo.isApplicable("/config/tenants/XM/a.yml")).isTrue();
        assertThat(repo.isApplicable("/config/tenants/XM/missing.yml")).isFalse();
        assertThat(repo.isApplicable("/config/tenants/XM")).isFalse(); // directory
    }

    @Test
    public void saveAll_writesFilesToDisk() throws IOException {
        FileRepository repo = repository(false);
        repo.saveAll(List.of(new Configuration("/config/tenants/XM/new.yml", "hello")), Map.of());

        Path written = tempDir.resolve("config/tenants/XM/new.yml");
        assertThat(Files.readString(written)).isEqualTo("hello");
    }

    @Test
    public void saveAll_withEmptyContent_deletesFile() throws IOException {
        FileRepository repo = repository(false);
        Path file = tempDir.resolve("config/tenants/XM/del.yml");
        Files.writeString(file, "old");

        repo.saveAll(List.of(new Configuration("/config/tenants/XM/del.yml", "")), Map.of());

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    public void deleteAll_removesFiles() throws IOException {
        FileRepository repo = repository(false);
        Path file = tempDir.resolve("config/tenants/XM/del.yml");
        Files.writeString(file, "old");

        repo.deleteAll(List.of("/config/tenants/XM/del.yml"));

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    public void writeOperations_throwWhenReadOnly() throws IOException {
        FileRepository repo = repository(true);
        assertThatThrownBy(() -> repo.saveAll(List.of(new Configuration("/config/x.yml", "y")), Map.of()))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.deleteAll(List.of("/config/x.yml")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void findAll_encodesBinaryFilesAsBase64() throws IOException {
        ApplicationProperties appProps = new ApplicationProperties();
        appProps.setBinaryFileTypes(List.of(".bin"));
        FileRepository repo = repository(false, appProps);
        byte[] bytes = {0, 1, 2, 3, 4};
        Files.write(tempDir.resolve("config/tenants/XM/file.bin"), bytes);

        Configuration cfg = repo.find("/config/tenants/XM/file.bin", new ConfigVersion("file"));

        assertThat(cfg.getContent()).isEqualTo(Base64.getEncoder().encodeToString(bytes));
    }
}
