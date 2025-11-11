package com.icthh.xm.ms.configuration.repository.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

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
    private S3Client s3Client;
    private S3Repository s3Repository;
    private String configPrefix;

    @Before
    public void setUp() {
        s3Client = mock(S3Client.class);
        s3Repository = new S3Repository(s3Client, TEST_BUCKET_NAME, configPath);
        configPrefix = configPath != null ? configPath + "/config/" : "config/";
    }

    @Test
    public void shouldSaveConfiguration() {
        var path = "/config/test.file";
        var content = "test-content";
        var config = new Configuration(path, content);
        s3Repository.save(config);
        var requestArgumentCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        var bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestArgumentCaptor.capture(), bodyCaptor.capture());
        assertEquals(TEST_BUCKET_NAME, requestArgumentCaptor.getValue().bucket());
        assertEquals(configPrefix + "test.file", requestArgumentCaptor.getValue().key());
        var stored = getBodyContent(bodyCaptor);
        assertEquals(content, stored);
    }

    private static String getBodyContent(ArgumentCaptor<RequestBody> bodyCaptor) {
        String stored;
        try (java.io.InputStream is = bodyCaptor.getValue().contentStreamProvider().newStream()) {
            stored = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read request body", e);
        }
        return stored;
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
        var deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteCaptor.capture());
        var deleted = deleteCaptor.getValue().delete().objects();
        assertEquals(2, deleted.size());
        var keys = deleted.stream().map(ObjectIdentifier::key).toList();
        assertEquals(List.of(configPrefix + "file1.yml", configPrefix + "file2.yml"), keys);
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
        var objects = List.of(
                S3Object.builder().key(configPrefix + "file1.yml").build(),
                S3Object.builder().key(configPrefix + "file2.yml").build()
        );
        var paginator = Mockito.mock(ListObjectsV2Iterable.class);
        when(paginator.contents()).thenReturn(objects::iterator);
        when(s3Client.listObjectsV2Paginator(ArgumentMatchers.<ListObjectsV2Request>argThat(request ->
                request != null && TEST_BUCKET_NAME.equals(request.bucket()) && configPrefix.equals(request.prefix()))))
                .thenReturn(paginator);

        Configuration config1 = new Configuration("/config/file3.yml", "new-content-3");
        Configuration config2 = new Configuration("/config/file4.yml", "new-content-4");
        List<Configuration> newConfigs = List.of(config1, config2);

        s3Repository.setRepositoryState(newConfigs);

        // verify batch delete
        ArgumentCaptor<DeleteObjectsRequest> delCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(delCaptor.capture());
        var deleted = delCaptor.getValue().delete().objects();
        assertEquals(2, deleted.size());
        var deletedKeys = deleted.stream().map(ObjectIdentifier::key).toList();
        assertEquals(List.of(configPrefix + "file1.yml", configPrefix + "file2.yml"), deletedKeys);

        // verify uploads of new configs
        verify(s3Client).putObject(ArgumentMatchers.<PutObjectRequest>argThat(
                        r -> r.bucket().equals(TEST_BUCKET_NAME) && r.key().equals(configPrefix + "file3.yml")),
                any(RequestBody.class));
        verify(s3Client).putObject(ArgumentMatchers.<PutObjectRequest>argThat(
                        r -> r.bucket().equals(TEST_BUCKET_NAME) && r.key().equals(configPrefix + "file4.yml")),
                any(RequestBody.class));
    }

    private void mockTenantS3Data(String tenantKey, String content) {
        mockS3ConfigData(configPrefix + "tenants/" + tenantKey + "/", TEST_FILE_YAML, content);
    }

    private void mockS3ConfigData(String contentPath, String contentFileName, String content) {
        // S3 paginator stub to return all objects under the prefix
        var objects = List.of(S3Object.builder().key(contentPath + contentFileName).build());
        var paginator = Mockito.mock(ListObjectsV2Iterable.class);
        when(paginator.contents()).thenReturn(objects::iterator);
        when(s3Client.listObjectsV2Paginator(ArgumentMatchers.<ListObjectsV2Request>argThat(request ->
                request != null && TEST_BUCKET_NAME.equals(request.bucket()) && contentPath.equals(request.prefix()))))
                .thenReturn(paginator);

        // mock getObject with ResponseTransformer.toBytes() using conditional Answer to avoid NPE
        var responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), content.getBytes(StandardCharsets.UTF_8));
        when(s3Client.getObject(
                any(GetObjectRequest.class),
                ArgumentMatchers.<ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>>>any())
        ).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            if (request != null && TEST_BUCKET_NAME.equals(request.bucket()) && (contentPath + contentFileName).equals(
                    request.key())) {
                return responseBytes;
            }
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[0]);
        });
    }
}
