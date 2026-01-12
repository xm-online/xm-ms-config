package com.icthh.xm.ms.configuration.repository.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.S3Rules;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepositoryStrategy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.services.s3.S3Client;

public class DynamicConfigRepositoryUnitTest {

    public static final ConfigVersion J_GIT_VERSION = new ConfigVersion("jGit");
    private static final ConfigVersion S_3_VERSION = new ConfigVersion("S3");
    private static final String CONFIG_1_PATH = "/config/s3/file1.yml";
    private static final String CONFIG_1_CONTENT_1 = "content1";
    private static final List<Configuration> S_3_CONFIGS = List.of(
            new Configuration(CONFIG_1_PATH, CONFIG_1_CONTENT_1),
            new Configuration("/config/s3-full-path.txt", "content2")
    );
    private static final ConfigurationList S_3_CONFIGURATION_LIST = new ConfigurationList(new ConfigVersion("S3"),
            S_3_CONFIGS);
    private static final List<Configuration> J_GIT_CONFIGS = List.of(
            new Configuration("/config/git/file2.yml", "content3"),
            new Configuration("/config/s3/uaa/file3.yml", "content4")
    );
    private static final List<Configuration> J_GIT_CONFIGS_WITH_LEGACY_CONFIG = List.of(
            new Configuration("/config/git/file2.yml", "content3"),
            new Configuration("/config/s3/uaa/file3.yml", "content4"),
            new Configuration(CONFIG_1_PATH, "content5")
    );
    private static final ConfigurationList J_GIT_CONFIGURATION_LIST_WITH_LEGACY = new ConfigurationList(
            J_GIT_VERSION, J_GIT_CONFIGS_WITH_LEGACY_CONFIG);

    private PersistenceConfigRepositoryStrategy jGitRepository;
    private PersistenceConfigRepositoryStrategy s3Repository;
    private DynamicConfigRepository repository;

    @Before
    public void setUp() {
        jGitRepository = mock(PersistenceConfigRepositoryStrategy.class);
        when(jGitRepository.isApplicable(any())).thenReturn(true);
        when(jGitRepository.priority()).thenReturn(Integer.MAX_VALUE);
        S3Client s3Client = mock(S3Client.class);
        S3Rules s3Rules = new S3Rules();
        s3Rules.setIncludePaths(List.of("/config/s3/*", "/config/s3-full-path.txt"));
        s3Rules.setExcludePaths(List.of("/config/s3/uaa/*"));
        s3Repository = spy(new S3Repository(s3Client, "/config", null, s3Rules));
        repository = new DynamicConfigRepository(List.of(jGitRepository, s3Repository));
    }

    @Test
    public void testSaveAllSavesAllConfigurations() {
        var allConfigs = Stream.concat(S_3_CONFIGS.stream(), J_GIT_CONFIGS.stream()).toList();
        Map<String, String> configHashes = Map.of();
        doReturn(S_3_VERSION).when(s3Repository).saveAll(anyList(), anyMap());

        repository.saveAll(allConfigs, configHashes);
        verify(jGitRepository).saveAll(J_GIT_CONFIGS, configHashes);
        verify(s3Repository).saveAll(S_3_CONFIGS, configHashes);
    }

    @Test
    public void testDeleteAllDeletesAllConfigurations() {
        var s3Paths = List.of(CONFIG_1_PATH, "/config/s3-full-path.txt");
        var gitPaths = List.of("/config/git/file2.yml", "/config/s3/uaa/file3.yml");
        var allPaths = Stream.concat(s3Paths.stream(), gitPaths.stream()).toList();
        doReturn(S_3_VERSION).when(s3Repository).deleteAll(s3Paths);

        repository.deleteAll(allPaths);
        verify(jGitRepository).deleteAll(gitPaths);
        verify(s3Repository).deleteAll(s3Paths);
    }

    @Test
    public void testFindAllReturnsAllConfigurations() {
        var allConfigs = Stream.concat(S_3_CONFIGS.stream(), J_GIT_CONFIGS_WITH_LEGACY_CONFIG.stream()).toList();
        var allConfigurationList = new ConfigurationList(S_3_VERSION, allConfigs);
        when(jGitRepository.findAll()).thenReturn(J_GIT_CONFIGURATION_LIST_WITH_LEGACY);
        doReturn(S_3_CONFIGURATION_LIST).when(s3Repository).findAll();

        var result = repository.findAll();
        var expectedConfigurations = allConfigurationList.getData();
        var actualConfigurations = result.getData();
        assertEquals(4, actualConfigurations.size());
        assertTrue(expectedConfigurations.containsAll(actualConfigurations));
        var file1 = findConfig1(actualConfigurations);
        assertNotNull(file1);
        assertEquals("content1", file1.getContent());
    }

    @Test
    public void testSetRepositoryStateDelegatesToCorrectRepositories() {
        var allConfigs = Stream.concat(S_3_CONFIGS.stream(), J_GIT_CONFIGS.stream()).toList();
        doReturn(S_3_VERSION).when(s3Repository).setRepositoryState(S_3_CONFIGS);

        repository.setRepositoryState(allConfigs);
        verify(jGitRepository).setRepositoryState(J_GIT_CONFIGS);
        verify(s3Repository).setRepositoryState(S_3_CONFIGS);
    }

    @Test
    public void testFindAllInTenantReturnsAllConfigurations() {
        var tenantKey = "tenant1";
        when(jGitRepository.findAllInTenant(tenantKey)).thenReturn(J_GIT_CONFIGURATION_LIST_WITH_LEGACY);
        doReturn(S_3_CONFIGURATION_LIST).when(s3Repository).findAllInTenant(tenantKey);

        var result = repository.findAllInTenant(tenantKey);
        var actualConfigurations = result.getData();
        assertEquals(4, actualConfigurations.size());
        assertTrue(actualConfigurations.containsAll(S_3_CONFIGS));
        assertTrue(actualConfigurations.containsAll(J_GIT_CONFIGS));
        var file1 = findConfig1(actualConfigurations);
        assertNotNull(file1);
        assertEquals("content1", file1.getContent());
    }

    @Test
    public void testFindAllInTenantsReturnsAllConfigurations() {
        var tenants = Set.of("tenant1", "tenant2");
        when(jGitRepository.findAllInTenants(tenants)).thenReturn(J_GIT_CONFIGURATION_LIST_WITH_LEGACY);
        doReturn(S_3_CONFIGURATION_LIST).when(s3Repository).findAllInTenants(tenants);

        var result = repository.findAllInTenants(tenants);
        var actualConfigurations = result.getData();
        assertEquals(4, actualConfigurations.size());
        assertTrue(actualConfigurations.containsAll(S_3_CONFIGS));
        assertTrue(actualConfigurations.containsAll(J_GIT_CONFIGS));
        var file1 = findConfig1(actualConfigurations);
        assertNotNull(file1);
        assertEquals(CONFIG_1_CONTENT_1, file1.getContent());
    }

    @Test
    public void testHasVersionDelegatesToJGitRepository() {
        when(jGitRepository.hasVersion(J_GIT_VERSION)).thenReturn(true);
        boolean result = repository.hasVersion(J_GIT_VERSION);
        assertTrue(result);
        verify(jGitRepository).hasVersion(J_GIT_VERSION);
        verify(s3Repository, never()).hasVersion(J_GIT_VERSION);
    }

    @Test
    public void testGetCurrentVersionDelegatesToJGitRepository() {
        when(jGitRepository.getCurrentVersion()).thenReturn(J_GIT_VERSION);
        ConfigVersion result = repository.getCurrentVersion();
        assertEquals(J_GIT_VERSION, result);
        verify(jGitRepository).getCurrentVersion();
        verify(s3Repository, never()).getCurrentVersion();
    }

    @Test
    public void testRecloneConfigurationDelegatesToJGitRepository() {
        repository.recloneConfiguration();
        verify(jGitRepository).recloneConfiguration();
        verify(s3Repository).recloneConfiguration();
    }

    private static Configuration findConfig1(List<Configuration> actualConfigurations) {
        return actualConfigurations.stream()
                .filter(conf -> CONFIG_1_PATH.equals(conf.getPath()))
                .findFirst()
                .orElse(null);
    }
}
