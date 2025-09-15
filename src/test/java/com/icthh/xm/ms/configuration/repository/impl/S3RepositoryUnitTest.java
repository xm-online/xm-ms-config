package com.icthh.xm.ms.configuration.repository.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.icthh.xm.commons.config.domain.Configuration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class S3RepositoryUnitTest {

    @Parameterized.Parameters(name = "configPath={0}")
    public static Object[] data() {
        return new Object[]{null, "xm-config"};
    }

    @Parameterized.Parameter
    public String configPath;

    private static final String TEST_FILE_YAML = "test-file.yaml";
    private static final String TEST_BUCKET_NAME = "test-bucket";
    private AmazonS3 s3Client;
    private S3Repository s3Repository;
    private String configPrefix;

    @Before
    public void setUp() {
        s3Client = mock(AmazonS3.class);
        s3Repository = new S3Repository(s3Client, TEST_BUCKET_NAME, configPath);
        configPrefix = configPath != null ? configPath + "/config/" : "config/";
    }

    @Test
    public void shouldSaveConfiguration() {
        String path = "/config/test.file";
        String content = "test-content";
        var config = new Configuration(path, content);
        s3Repository.save(config);
        verify(s3Client).putObject(eq(TEST_BUCKET_NAME), eq(configPrefix + "test.file"), eq(content));
    }

    @Test
    public void shouldFindConfigs() {
        mockS3ConfigData(configPrefix, "roles.yaml", "test-content");
        var configs = s3Repository.findAll();
        var configData = configs.getData();
        assertNotNull(configData);
        assertEquals(1, configData.size());
        assertEquals("test-content", configData.getFirst().getContent());
    }

    @Test
    public void shouldFindConfigsForTenants() {
        mockTenantS3Data("TENANT1", "tenant1_content");
        mockTenantS3Data("TENANT2", "tenant2_content");
        mockTenantS3Data("TENANT3", "tenant3_content");
        var allConfigs = s3Repository.findAllInTenants(Set.of("TENANT1", "TENANT3"));
        assertEquals(2, allConfigs.getData().size());
    }

    @Test
    public void shouldDeleteAllConfigurations() {
        List<String> paths = List.of("/config/file1.yml", "/config/file2.yml");
        s3Repository.deleteAll(paths);
        verify(s3Client).deleteObject(TEST_BUCKET_NAME, configPrefix + "file1.yml");
        verify(s3Client).deleteObject(TEST_BUCKET_NAME, configPrefix + "file2.yml");
    }

    @Test
    public void shouldFindConfigurationByPath() {
        String path = "/config/test/test.file";
        mockS3ConfigData(configPrefix + "test/", "test.file", "test-content-find");
        var configItem = s3Repository.find(path);
        assertEquals("test-content-find", configItem.getData().getContent());
        assertEquals(path, configItem.getData().getPath());
    }

    @Test
    public void shouldSetRepositoryState() {
        ObjectListing listing = mock(ObjectListing.class);
        S3ObjectSummary summary1 = new S3ObjectSummary();
        summary1.setKey("config/file1.yml");
        S3ObjectSummary summary2 = new S3ObjectSummary();
        summary2.setKey("config/file2.yml");
        when(listing.getObjectSummaries()).thenReturn(List.of(summary1, summary2));
        when(s3Client.listObjects(eq(TEST_BUCKET_NAME), eq(configPrefix))).thenReturn(listing);

        Configuration config1 = new Configuration("/config/file3.yml", "new-content-3");
        Configuration config2 = new Configuration("/config/file4.yml", "new-content-4");
        List<Configuration> newConfigs = List.of(config1, config2);

        s3Repository.setRepositoryState(newConfigs);
        verify(s3Client).deleteObject(TEST_BUCKET_NAME, configPrefix + "file1.yml");
        verify(s3Client).deleteObject(TEST_BUCKET_NAME, configPrefix + "file2.yml");
        verify(s3Client).putObject(TEST_BUCKET_NAME, configPrefix + "file3.yml", "new-content-3");
        verify(s3Client).putObject(TEST_BUCKET_NAME, configPrefix + "file4.yml", "new-content-4");
    }

    private void mockTenantS3Data(String tenantKey, String content) {
        mockS3ConfigData(configPrefix + "tenants/" + tenantKey + "/", TEST_FILE_YAML, content);
    }

    private void mockS3ConfigData(String contentPath, String contentFileName, String content) {
        var listing = mock(ObjectListing.class);
        var summary = new S3ObjectSummary();
        summary.setKey(contentPath + contentFileName);
        when(listing.getObjectSummaries()).thenReturn(List.of(summary));
        when(s3Client.listObjects(eq(TEST_BUCKET_NAME), eq(contentPath))).thenReturn(listing);

        S3Object s3Object = mock(S3Object.class);
        when(s3Client.getObject(eq(TEST_BUCKET_NAME), eq(contentPath + contentFileName))).thenReturn(s3Object);
        S3ObjectInputStream s3InputStream = new S3ObjectInputStream(
                new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                mock(HttpRequestBase.class)
        );
        when(s3Object.getObjectContent()).thenReturn(s3InputStream);
    }
}
