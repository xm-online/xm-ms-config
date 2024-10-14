package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.commons.tenant.TenantContextUtils.buildTenant;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.Tenant;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationServiceUnitTest {

    public static final String TENANT_NAME = "tenant";
    @InjectMocks
    private ConfigurationService configurationService;
    @Mock
    private MemoryConfigStorage memoryStorage;
    @Mock
    private PersistenceConfigRepository persistenceRepository;
    @Mock
    private ConfigTopicProducer configTopicProducer;
    @Mock
    private TenantContextHolder tenantContextHolder;
    @Spy
    private ApplicationProperties applicationProperties = new ApplicationProperties();
    @Mock
    private ConfigVersionDeserializer configVersionDeserializer;
    @Spy
    private VersionCache versionCache = new VersionCache(applicationProperties);
    @Spy
    private Lock lock = new ReentrantLock();

    @Before
    public void before() {
        when(tenantContextHolder.getContext()).thenReturn(new TenantContext() {
            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public Optional<Tenant> getTenant() {
                return Optional.of(buildTenant("TENANT_NAME"));
            }
        });
    }

    @Test
    public void getConfigurationMap_shouldReturnProcessedConfigs() {
        when(memoryStorage.getProcessedConfigs()).thenReturn(Map.of("path", new Configuration("path", "content")));

        Map<String, Configuration> result = configurationService.getConfigurationMap("someVersion");

        assertEquals(1, result.size());
        assertEquals("content", result.get("path").getContent());
    }

    @Test
    public void getConfigurationMapWithPaths_shouldReturnProcessedConfigsForPaths() {
        when(memoryStorage.getProcessedConfigs(List.of("path"))).thenReturn(Map.of("path", new Configuration("path", "content")));

        Map<String, Configuration> result = configurationService.getConfigurationMap("someVersion", List.of("path"));

        assertEquals(1, result.size());
        assertEquals("content", result.get("path").getContent());
    }

    @Test
    public void alignVersion_shouldUpdateConfigIfNotOnCommit() {
        ConfigVersion version = new ConfigVersion("someVersion");
        when(configVersionDeserializer.from("someVersion")).thenReturn(version);
        when(persistenceRepository.hasVersion(version)).thenReturn(false);
        when(persistenceRepository.findAll()).thenReturn(new ConfigurationList(version, List.of(
            new Configuration("path", "content"),
            new Configuration("path2", "content2")
        )));
        when(memoryStorage.getProcessedConfigs()).thenReturn(Map.of(
            "path3", new Configuration("path3", "content3"),
            "path4", new Configuration("path4", "content4")
        ));

        configurationService.getConfigurationMap("someVersion");

        verify(persistenceRepository).findAll();
        verify(versionCache).addVersion(version);
        verify(memoryStorage).replaceByConfiguration(eq(
            List.of(
                new Configuration("path", "content"),
                new Configuration("path2", "content2")
            )
        ));
        verifyZeroInteractions(configTopicProducer);
    }

    @Test
    public void alignVersion_shouldNotUpdateConfigIfOnCommit() {
        ConfigVersion version = new ConfigVersion("someVersion");
        when(configVersionDeserializer.from("someVersion")).thenReturn(version);
        when(persistenceRepository.hasVersion(version)).thenReturn(true);
        when(memoryStorage.getProcessedConfigs()).thenReturn(Map.of(
            "path", new Configuration("path", "content"),
            "path2", new Configuration("path2", "content2")
        ));

        configurationService.getConfigurationMap("someVersion");

        verify(persistenceRepository, never()).findAll();
        verify(memoryStorage, never()).replaceByConfiguration(anyList());
        verifyZeroInteractions(configTopicProducer);
    }

    @Test
    public void alignVersion_shouldNotUpdateConfigIfVersionInCache() {
        ConfigVersion version = new ConfigVersion("someVersion");
        when(configVersionDeserializer.from("someVersion")).thenReturn(version);
        when(persistenceRepository.hasVersion(version)).thenReturn(false);
        versionCache.addVersion(version);
        when(memoryStorage.getProcessedConfigs()).thenReturn(Map.of(
            "path", new Configuration("path", "content"),
            "path2", new Configuration("path2", "content2")
        ));

        configurationService.getConfigurationMap("someVersion");

        verify(persistenceRepository, never()).findAll();
        verify(memoryStorage, never()).replaceByConfiguration(anyList());
        verifyZeroInteractions(configTopicProducer);
    }

    @Test
    public void findConfiguration_shouldReturnConfigWhenVersionIsNull() {
        String path = "/config/test";
        Configuration config = new Configuration(path, "content");
        when(memoryStorage.getConfig(path)).thenReturn(Optional.of(config));

        Optional<Configuration> result = configurationService.findConfiguration(path, null);

        assertTrue(result.isPresent());
        assertEquals("content", result.get().getContent());
        verify(memoryStorage).getConfig(eq(path));
        verifyZeroInteractions(persistenceRepository);
        verifyNoMoreInteractions(memoryStorage);
    }

    @Test
    public void findConfiguration_shouldReturnConfigFromPersistenceWhenVersionIsDefined() {
        String path = "/config/test";
        ConfigVersion version = new ConfigVersion("someVersion");
        Configuration config = new Configuration(path, "content");
        when(persistenceRepository.find(path, version)).thenReturn(config);

        Optional<Configuration> result = configurationService.findConfiguration(path, version);

        assertTrue(result.isPresent());
        assertEquals("content", result.get().getContent());
        verify(persistenceRepository).find(eq(path), eq(version));
        verifyNoMoreInteractions(persistenceRepository);
        verifyZeroInteractions(memoryStorage);
    }

    @Test
    public void findProcessedConfiguration_shouldReturnProcessedConfigWhenProcessedIsTrue() {
        String path = "/config/test";
        Configuration config = new Configuration(path, "processedContent");
        when(memoryStorage.getProcessedConfig(path)).thenReturn(Optional.of(config));

        Optional<Configuration> result = configurationService.findProcessedConfiguration(path, true);

        assertTrue(result.isPresent());
        assertEquals("processedContent", result.get().getContent());
        verify(memoryStorage).getProcessedConfig(eq(path));
        verifyNoMoreInteractions(memoryStorage);
        verifyZeroInteractions(persistenceRepository);
    }

    @Test
    public void findProcessedConfiguration_shouldReturnConfigWhenProcessedIsFalse() {
        String path = "/config/test";
        Configuration config = new Configuration(path, "content");
        when(memoryStorage.getConfig(path)).thenReturn(Optional.of(config));

        Optional<Configuration> result = configurationService.findProcessedConfiguration(path, false);

        assertTrue(result.isPresent());
        assertEquals("content", result.get().getContent());
        verify(memoryStorage).getConfig(eq(path));
        verifyNoMoreInteractions(memoryStorage);
        verifyZeroInteractions(persistenceRepository);
    }

    @Test
    public void findConfiguration_shouldReturnConfigFromMemory() {
        String path = "/config/test";
        Configuration config = new Configuration(path, "content");
        when(memoryStorage.getConfig(path)).thenReturn(Optional.of(config));

        Optional<Configuration> result = configurationService.findConfiguration(path);

        assertTrue(result.isPresent());
        assertEquals("content", result.get().getContent());
        verify(memoryStorage).getConfig(eq(path));
        verifyNoMoreInteractions(memoryStorage);
        verifyZeroInteractions(persistenceRepository);
    }

    @Test
    public void testFindTenantConfigurationsPathsNormalizationAndFilter() {
        List<String> paths = List.of("/config/tenants/TENANT_NAME/folder/../../OTHER_TENANT/subfolder", "/invalid/path",
            "/config/tenants/TENANT_NAME/folder/subfolder/../configfile.yml", "/config/tenants/TENANT_NAME/folder/configfile2.yml");
        List<String> okPaths = List.of("/config/tenants/TENANT_NAME/folder/configfile.yml", "/config/tenants/TENANT_NAME/folder/configfile2.yml");
        when(memoryStorage.getConfigs(eq(okPaths))).thenReturn(List.of(
            new Configuration("/config/tenants/TENANT_NAME/folder/configfile.yml", "content"),
            new Configuration("/config/tenants/TENANT_NAME/folder/configfile2.yml", "content2")
        ));

        var result = configurationService.findTenantConfigurations(paths, false);
        assertEquals(2, result.size());
        assertEquals(Map.of(
            "/config/tenants/TENANT_NAME/folder/configfile.yml", new Configuration("/config/tenants/TENANT_NAME/folder/configfile.yml", "content"),
            "/config/tenants/TENANT_NAME/folder/configfile2.yml", new Configuration("/config/tenants/TENANT_NAME/folder/configfile2.yml", "content2")
        ), result);
        verify(memoryStorage).getConfigs(eq(okPaths));
        verifyZeroInteractions(persistenceRepository);
        verifyNoMoreInteractions(memoryStorage);
    }

    @Test
    public void testFindTenantConfigurationsPathsFetchAll() {
        List<String> paths = List.of("/config/tenants/TENANT_NAME/folder/configfile.yml");
        when(memoryStorage.getConfigsFromTenant(eq("TENANT_NAME"))).thenReturn(List.of(
            new Configuration("/config/tenants/TENANT_NAME/folder/configfile.yml", "content")
        ));

        var result = configurationService.findTenantConfigurations(paths, true);
        assertEquals(1, result.size());
        assertEquals(Map.of(
            "/config/tenants/TENANT_NAME/folder/configfile.yml", new Configuration("/config/tenants/TENANT_NAME/folder/configfile.yml", "content")
        ), result);
        verify(memoryStorage).getConfigsFromTenant(eq("TENANT_NAME"));
        verifyZeroInteractions(persistenceRepository);
        verifyNoMoreInteractions(memoryStorage);
    }

}
