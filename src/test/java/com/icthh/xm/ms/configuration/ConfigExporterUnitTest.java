package com.icthh.xm.ms.configuration;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeService;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigExporterUnitTest extends AbstractUnitTest {

    private static final String REPO_PATH = "repo-path";
    private static final String EXPORT_ZIP = "export.zip";
    public static final String MAIN_CONTENT = "MAIN: value";
    public static final String MAIN_PATH_CONFIG = "/config/tenants/MAIN/app.yml";

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private TenantAliasTreeService tenantAliasTreeService;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    @TempDir
    Path tempDir;

    @Test
    void main_withAliasConfig_updatesAliasTreeAndWritesZip() throws IOException {
        String submainContent = "SUBMAIN: value";
        String submainPath = "/config/tenants/SUBMAIN/app.yml";
        String aliasContent = "tenantAliasTree: - key: MAIN children: - key: SUBMAIN";
        Configuration aliasConfig = new Configuration(TenantAliasTreeService.TENANT_ALIAS_CONFIG, aliasContent);

        when(configurationService.findConfiguration(TenantAliasTreeService.TENANT_ALIAS_CONFIG))
            .thenReturn(Optional.of(aliasConfig));
        when(configurationService.getConfigurationMap(null))
            .thenReturn(Map.of(
                MAIN_PATH_CONFIG, new Configuration(MAIN_PATH_CONFIG, MAIN_CONTENT),
                submainPath, new Configuration(submainPath, submainContent)
            ));

        String outputPath = tempDir.resolve(EXPORT_ZIP).toString();

        runMain(outputPath);

        verify(tenantAliasTreeService).updateAliasTree(eq(aliasConfig));

        Map<String, String> zipEntries = readZipEntries(outputPath);
        assertEquals(2, zipEntries.size());
        assertEquals(MAIN_CONTENT, zipEntries.get(MAIN_PATH_CONFIG.substring(1)));
        assertEquals(submainContent, zipEntries.get(submainPath.substring(1)));
    }

    @Test
    void main_withoutAliasConfig_skipsAliasTreeUpdate() throws IOException {
        when(configurationService.findConfiguration(TenantAliasTreeService.TENANT_ALIAS_CONFIG))
            .thenReturn(Optional.empty());
        when(configurationService.getConfigurationMap(null))
            .thenReturn(Map.of(
                MAIN_PATH_CONFIG, new Configuration(MAIN_PATH_CONFIG, MAIN_CONTENT)
            ));

        String outputPath = tempDir.resolve(EXPORT_ZIP).toString();
        runMain(outputPath);

        verify(tenantAliasTreeService, never()).updateAliasTree(any());

        Map<String, String> zipEntries = readZipEntries(outputPath);
        assertEquals(1, zipEntries.size());
        assertEquals(MAIN_CONTENT, zipEntries.get(MAIN_PATH_CONFIG.substring(1)));
    }

    @Test
    void main_multipleConfigs_allWrittenToZip() throws IOException {
        when(configurationService.findConfiguration(TenantAliasTreeService.TENANT_ALIAS_CONFIG))
            .thenReturn(Optional.empty());

        Map<String, Configuration> configs = new LinkedHashMap<>();
        configs.put("/config/app.yml", new Configuration("/config/app.yml", "app"));
        configs.put("/config/db.yml", new Configuration("/config/db.yml", "db"));
        configs.put("/config/cache.yml", new Configuration("/config/cache.yml", "cache"));
        when(configurationService.getConfigurationMap(null)).thenReturn(configs);

        String outputPath = tempDir.resolve(EXPORT_ZIP).toString();
        runMain(outputPath);

        Map<String, String> zipEntries = readZipEntries(outputPath);
        assertEquals(3, zipEntries.size());
        assertEquals("app", zipEntries.get("config/app.yml"));
        assertEquals("db", zipEntries.get("config/db.yml"));
        assertEquals("cache", zipEntries.get("config/cache.yml"));
    }

    @Test
    void main_directoryAsOutputPath_createsZipWithDefaultFilename() throws IOException {
        when(configurationService.findConfiguration(TenantAliasTreeService.TENANT_ALIAS_CONFIG))
            .thenReturn(Optional.empty());
        when(configurationService.getConfigurationMap(null))
            .thenReturn(Map.of(
                MAIN_PATH_CONFIG, new Configuration(MAIN_PATH_CONFIG, MAIN_CONTENT)
            ));

        String dirPath = tempDir.toFile().getAbsolutePath();
        runMain(dirPath);

        File expectedFile = new File(dirPath, "config-export.zip");
        assertTrue(expectedFile.exists());

        Map<String, String> zipEntries = readZipEntries(expectedFile.getAbsolutePath());
        assertEquals(1, zipEntries.size());
    }

    @Test
    void main_emptyConfigMap_createsEmptyZip() throws IOException {
        when(configurationService.findConfiguration(TenantAliasTreeService.TENANT_ALIAS_CONFIG))
            .thenReturn(Optional.empty());
        when(configurationService.getConfigurationMap(null)).thenReturn(Map.of());

        String outputPath = tempDir.resolve("empty.zip").toString();
        runMain(outputPath);

        assertTrue(new File(outputPath).exists());
        Map<String, String> zipEntries = readZipEntries(outputPath);
        assertTrue(zipEntries.isEmpty());
    }

    private void runMain(String outputPath) {
        when(applicationContext.getBean(ConfigurationService.class)).thenReturn(configurationService);
        when(applicationContext.getBean(TenantAliasTreeService.class)).thenReturn(tenantAliasTreeService);

        new ConfigExporter() {
            @Override
            protected ConfigurableApplicationContext createApplicationContext(String repoPath) {
                return applicationContext;
            }
        }.execute(new String[]{REPO_PATH, outputPath});
    }

    private Map<String, String> readZipEntries(String zipPath) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    stream.write(buffer, 0, len);
                }
                result.put(entry.getName(), stream.toString(StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return result;
    }
}
