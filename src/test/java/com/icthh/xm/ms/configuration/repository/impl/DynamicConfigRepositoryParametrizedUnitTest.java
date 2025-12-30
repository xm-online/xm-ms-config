package com.icthh.xm.ms.configuration.repository.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.S3Rules;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepositoryStrategy;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import software.amazon.awssdk.services.s3.S3Client;

@RunWith(Parameterized.class)
public class DynamicConfigRepositoryParametrizedUnitTest {

    private PersistenceConfigRepositoryStrategy jGitRepository;
    private PersistenceConfigRepositoryStrategy s3Repository;
    private DynamicConfigRepository repository;

    @Parameters(name = "path={0},expectedRepo={1}")
    public static Object[][] data() {
        return new Object[][]{
                {"/config/git/test.txt", "git"},
                {"/config/git/another.txt", "git"},
                {"/config/git-full-path.txt", "git"},
                {"/config/s3/test.txt", "s3"},
                {"/config/s3-full-path.txt", "s3"}
        };
    }

    @Parameter()
    public String path;
    @Parameter(1)
    public String expectedRepo;

    @Before
    public void setUp() {
        jGitRepository = mock(PersistenceConfigRepositoryStrategy.class);
        when(jGitRepository.isApplicable(any())).thenReturn(true);
        when(jGitRepository.priority()).thenReturn(Integer.MAX_VALUE);
        S3Rules s3Rules = new S3Rules();
        s3Rules.setIncludePaths(List.of("/config/s3/*", "/config/s3-full-path.txt"));
        s3Rules.setExcludePaths(List.of("/config/s3/uaa/*"));
        var s3Client = mock(S3Client.class);
        s3Repository = spy(new S3Repository(s3Client, "/config", "null", s3Rules));
        repository = new DynamicConfigRepository(List.of(s3Repository, jGitRepository));
    }

    @Test
    public void testFindDelegatesToCorrectRepo() {
        var item = mock(ConfigurationItem.class);
        when(jGitRepository.find(path)).thenReturn(item);
        doReturn(item).when(s3Repository).find(path);

        var result = repository.find(path);
        assertEquals(item, result);
        if ("git".equals(expectedRepo)) {
            verify(jGitRepository).find(path);
            verify(s3Repository, never()).find(path);
        } else if ("s3".equals(expectedRepo)) {
            verify(s3Repository).find(path);
            verify(jGitRepository, never()).find(path);
        }
    }

    @Test
    public void testFindWithVersionDelegatesToCorrectRepo() {
        var configuration = mock(Configuration.class);
        var version = new ConfigVersion("TST");
        when(jGitRepository.find(path, version)).thenReturn(configuration);
        doReturn(configuration).when(s3Repository).find(path, version);

        var result = repository.find(path, version);
        assertEquals(configuration, result);
        if ("git".equals(expectedRepo)) {
            verify(jGitRepository).find(path, version);
            verify(s3Repository, never()).find(path, version);
        } else if ("s3".equals(expectedRepo)) {
            verify(s3Repository).find(path, version);
            verify(jGitRepository, never()).find(path, version);
        }
    }

    @Test
    public void testSaveDelegatesToCorrectRepo() {
        var config = mock(Configuration.class);
        when(config.getPath()).thenReturn(path);
        var version = new ConfigVersion(expectedRepo);
        when(jGitRepository.save(config)).thenReturn(version);
        doReturn(version).when(s3Repository).save(config);

        var result = repository.save(config);
        assertEquals(version, result);
        if ("git".equals(expectedRepo)) {
            verify(jGitRepository).save(config);
            verify(s3Repository, never()).save(config);
        } else if ("s3".equals(expectedRepo)) {
            verify(s3Repository).save(config);
            verify(jGitRepository, never()).save(config);
        }
    }

    @Test
    public void testDeleteAllDelegatesToCorrectRepo() {
        var paths = List.of(path);
        var version = new ConfigVersion(expectedRepo);
        when(jGitRepository.deleteAll(paths)).thenReturn(version);
        doReturn(version).when(s3Repository).deleteAll(paths);

        var result = repository.deleteAll(paths);
        assertEquals(version, result);
        if ("git".equals(expectedRepo)) {
            verify(jGitRepository).deleteAll(paths);
            verify(s3Repository, never()).deleteAll(paths);
        } else if ("s3".equals(expectedRepo)) {
            verify(s3Repository).deleteAll(paths);
            verify(jGitRepository, never()).deleteAll(paths);
        }
    }
}

