package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.Tenant;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.AbstractUnitTest;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.icthh.xm.commons.tenant.TenantContextUtils.buildTenant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ConfigurationServiceUnitTest extends AbstractUnitTest {

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
    @Mock
    private ApplicationEventPublisher publisher;
    @Spy
    private VersionCache versionCache = new VersionCache(applicationProperties);
    @Spy
    private Lock lock = new ReentrantLock();

    @BeforeEach
    public void before() {
        Mockito.lenient().when(tenantContextHolder.getContext()).thenReturn(new TenantContext() {
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
        verifyNoMoreInteractions(configTopicProducer);
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
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void alignVersion_shouldNotUpdateConfigIfVersionInCache() {
        ConfigVersion version = new ConfigVersion("someVersion");
        when(configVersionDeserializer.from("someVersion")).thenReturn(version);
        versionCache.addVersion(version);
        when(memoryStorage.getProcessedConfigs()).thenReturn(Map.of(
            "path", new Configuration("path", "content"),
            "path2", new Configuration("path2", "content2")
        ));

        configurationService.getConfigurationMap("someVersion");

        verify(persistenceRepository, never()).findAll();
        verify(memoryStorage, never()).replaceByConfiguration(anyList());
        verifyNoMoreInteractions(configTopicProducer);
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
        verifyNoMoreInteractions(persistenceRepository);
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
        verifyNoMoreInteractions(memoryStorage);
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
        verifyNoMoreInteractions(persistenceRepository);
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
        verifyNoMoreInteractions(persistenceRepository);
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
        verifyNoMoreInteractions(persistenceRepository);
    }

    @Test
    public void testFindTenantConfigurationsPathsNormalizationAndFilter() {
        List<String> paths = List.of("/config/tenants/TENANT_NAME/folder/../../OTHER_TENANT/subfolder", "/invalid/path",
            "/config/tenants/TENANT_NAME/folder/subfolder/../configfile.yml", "/config/tenants/TENANT_NAME/folder/configfile2.yml");
        List<String> okPaths = List.of("/config/tenants/TENANT_NAME/folder/configfile.yml", "/config/tenants/TENANT_NAME/folder/configfile2.yml");
        when(memoryStorage.getConfigs(eq("TENANT_NAME"), eq(okPaths))).thenReturn(List.of(
            new Configuration("/config/tenants/TENANT_NAME/folder/configfile.yml", "content"),
            new Configuration("/config/tenants/TENANT_NAME/folder/configfile2.yml", "content2")
        ));

        var result = configurationService.findTenantConfigurations(paths, false);
        assertEquals(2, result.size());
        assertEquals(Map.of(
            "/config/tenants/TENANT_NAME/folder/configfile.yml", new Configuration("/config/tenants/TENANT_NAME/folder/configfile.yml", "content"),
            "/config/tenants/TENANT_NAME/folder/configfile2.yml", new Configuration("/config/tenants/TENANT_NAME/folder/configfile2.yml", "content2")
        ), result);
        verify(memoryStorage).getConfigs(eq("TENANT_NAME"), eq(okPaths));
        verifyNoMoreInteractions(persistenceRepository);
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
        verifyNoMoreInteractions(persistenceRepository);
        verifyNoMoreInteractions(memoryStorage);
    }

    @Test
    public void updateConfiguration_shouldBeRejectedWhenPersistenceUpdateDisabled() {
        applicationProperties.setUpdateConfigAvailable(false);

        assertThrows(AccessDeniedException.class,
            () -> configurationService.updateConfiguration(new Configuration("path", "content")));

        verify(persistenceRepository, never()).saveAll(anyList(), anyMap());
        verify(memoryStorage, never()).saveConfigs(anyList());
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void deleteConfigurations_shouldBeRejectedWhenPersistenceUpdateDisabled() {
        applicationProperties.setUpdateConfigAvailable(false);

        assertThrows(AccessDeniedException.class,
            () -> configurationService.deleteConfigurations(List.of("path")));

        verify(persistenceRepository, never()).deleteAll(anyList());
        verify(memoryStorage, never()).remove(anyList());
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void updateConfigurationInMemory_shouldBeRejectedWhenInMemoryUpdateDisabled() {
        applicationProperties.setUpdateConfigInMemoryAvailable(false);

        assertThrows(AccessDeniedException.class,
            () -> configurationService.updateConfigurationInMemory(List.of(new Configuration("path", "content"))));

        verify(memoryStorage, never()).saveConfigs(anyList());
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void deleteConfigurationInMemory_shouldBeRejectedWhenInMemoryUpdateDisabled() {
        applicationProperties.setUpdateConfigInMemoryAvailable(false);

        assertThrows(AccessDeniedException.class,
            () -> configurationService.deleteConfigurationInMemory(List.of("path")));

        verify(memoryStorage, never()).remove(anyList());
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void updateConfigurationInMemory_shouldAllowJwkCacheUpdateWhenInMemoryUpdateDisabled() {
        applicationProperties.setUpdateConfigInMemoryAvailable(false);
        List<Configuration> jwks = List.of(new Configuration(
            "/config/tenants/XM/config/idp/clients/Google-jwks-cache.json", "{\"keys\":[]}"));
        when(memoryStorage.saveConfigs(jwks)).thenReturn(Set.of());

        configurationService.updateConfigurationInMemory(jwks);

        verify(memoryStorage).saveConfigs(jwks);
    }

    @Test
    public void updateConfigurationInMemory_shouldRejectMixedJwkAndNonJwkWhenInMemoryUpdateDisabled() {
        applicationProperties.setUpdateConfigInMemoryAvailable(false);
        List<Configuration> mixed = List.of(
            new Configuration("/config/tenants/XM/config/idp/clients/Google-jwks-cache.json", "{}"),
            new Configuration("/config/tenants/XM/other.yml", "a: b"));

        assertThrows(AccessDeniedException.class, () -> configurationService.updateConfigurationInMemory(mixed));

        verify(memoryStorage, never()).saveConfigs(anyList());
    }

    @Test
    public void inMemoryUpdateDisabled_shouldStillAllowRefreshFromPersistence() {
        applicationProperties.setUpdateConfigInMemoryAvailable(false);
        ConfigVersion version = new ConfigVersion("v1");
        List<Configuration> data = List.of(new Configuration("path", "content"));
        when(persistenceRepository.findAll()).thenReturn(new ConfigurationList(version, data));
        when(memoryStorage.replaceByConfiguration(anyList())).thenReturn(Set.of());

        configurationService.refreshConfiguration();

        verify(persistenceRepository).findAll();
        verify(memoryStorage).replaceByConfiguration(eq(data));
        verify(versionCache).addVersion(version);
    }

}
