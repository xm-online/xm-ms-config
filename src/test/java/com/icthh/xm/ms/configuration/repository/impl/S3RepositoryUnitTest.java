package com.icthh.xm.ms.configuration.repository.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.S3Rules;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
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
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
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
        s3Repository = new S3Repository(s3Client, TEST_BUCKET_NAME, configPath, new S3Rules());
        configPrefix = configPath != null ? configPath + "/config/" : "config/";
    }

    @Test
    public void shouldSaveConfiguration() {
        var path = "/config/test.file";
        var content = "test-content";
        var config = new Configuration(path, content);
        var bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        var testVersion = "test-version";
        mockPutS3Object(bodyCaptor, "test.file", testVersion);
        var result = s3Repository.save(config);
        assertNotNull(result);
        assertEquals(testVersion, result.getMainVersion());
        var stored = getBodyContent(bodyCaptor.getValue());
        assertEquals(content, stored);
    }

    @Test
    public void shouldFindConfigs() {
        mockGetListS3Objects(configPrefix, "roles.yaml", "test-content");
        var configs = s3Repository.findAll();
        var configData = configs.getData();
        assertNotNull(configData);
        assertEquals(1, configData.size());
        assertEquals("test-content", configData.getFirst().getContent());
    }

    @Test
    public void shouldFindConfigsForTenants() {
        mockGetListS3Objects("TENANT1", "tenant1_content");
        mockGetListS3Objects("TENANT2", "tenant2_content");
        mockGetListS3Objects("TENANT3", "tenant3_content");
        var allConfigs = s3Repository.findAllInTenants(Set.of("TENANT1", "TENANT3"));
        assertEquals(2, allConfigs.getData().size());
    }

    @Test
    public void shouldDeleteAllConfigurations() {
        var paths = List.of("/config/file1.yml", "/config/file2.yml");
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
        var path = "/config/test/test.file";
        mockGetS3Object(configPrefix + "test/", "test.file", "test-content-find", null);
        var configItem = s3Repository.find(path);
        assertEquals("test-content-find", configItem.getData().getContent());
        assertEquals(path, configItem.getData().getPath());
    }

    @Test
    public void shouldFindConfigurationByPathAndVersion() {
        var path = "/config/test/test.file";
        var version = "test-version";
        mockGetS3Object(configPrefix + "test/", "test.file", "test-content-find", version);
        var configuration = s3Repository.find(path, new ConfigVersion(version));
        assertEquals("test-content-find", configuration.getContent());
        assertEquals(path, configuration.getPath());
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

        var config1 = new Configuration("/config/file3.yml", "new-content-3");
        var config2 = new Configuration("/config/file4.yml", "new-content-4");
        var newConfigs = List.of(config1, config2);
        var testVersion = "test-version";
        var bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        mockPutS3Object(bodyCaptor, "file3.yml", testVersion);
        mockPutS3Object(bodyCaptor, "file4.yml", testVersion);

        var result = s3Repository.setRepositoryState(newConfigs);
        assertNotNull(result);
        assertEquals("s3", result.getMainVersion());

        // verify batch delete
        ArgumentCaptor<DeleteObjectsRequest> delCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(delCaptor.capture());
        var deleted = delCaptor.getValue().delete().objects();
        assertEquals(2, deleted.size());
        var deletedKeys = deleted.stream().map(ObjectIdentifier::key).toList();
        assertEquals(List.of(configPrefix + "file1.yml", configPrefix + "file2.yml"), deletedKeys);
    }

    @Test
    public void shouldSaveAllConfigurations() {
        var configs = List.of(
                new Configuration("/config/file1.yml", "content-1"),
                new Configuration("/config/file2.yml", "content-2")
        );
        var bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        when(s3Client.putObject(any(PutObjectRequest.class), bodyCaptor.capture()))
                .thenReturn(PutObjectResponse.builder().versionId("any-version").build());
        var result = s3Repository.saveAll(configs, Map.of());
        assertNotNull(result);
        assertEquals("s3", result.getMainVersion());
        assertEquals(2, bodyCaptor.getAllValues().size());
        var storedContents = bodyCaptor.getAllValues().stream()
                .map(this::getBodyContent)
                .toList();
        assertEquals(List.of("content-1", "content-2"), storedContents);
    }

    @Test
    public void shouldThrowConcurrentModificationOnHashMismatch() {
        var existingContent = "existing-content";
        var fileName = "file1.yml";
        mockGetS3Object(configPrefix, fileName, existingContent, null);
        var wrongHash = DigestUtils.sha1Hex("other-content");
        var config = new Configuration("/config/" + fileName, "new-content");
        assertThrows(
                ConcurrentConfigModificationException.class,
                () -> s3Repository.saveAll(List.of(config), Map.of(config.getPath(), wrongHash))
        );
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    public void shouldSaveWhenHashMatches() {
        var existingContent = "existing-content";
        var fileName = "file2.yml";
        mockGetS3Object(configPrefix, fileName, existingContent, null);
        var correctHash = DigestUtils.sha1Hex(existingContent);
        var config = new Configuration("/config/" + fileName, "updated-content");
        var bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        when(s3Client.putObject(any(PutObjectRequest.class), bodyCaptor.capture()))
                .thenReturn(PutObjectResponse.builder().versionId("any-version").build());
        var result = s3Repository.saveAll(List.of(config), Map.of(config.getPath(), correctHash));
        assertNotNull(result);
        assertEquals("s3", result.getMainVersion());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertEquals(1, bodyCaptor.getAllValues().size());
    }

    private void mockPutS3Object(ArgumentCaptor<RequestBody> bodyCaptor, String fileName,
            String testVersion) {
        when(s3Client.putObject(any(PutObjectRequest.class), bodyCaptor.capture())).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            if (request != null && TEST_BUCKET_NAME.equals(request.bucket()) && (configPrefix + fileName).equals(
                    request.key())) {
                return PutObjectResponse.builder().versionId(testVersion).build();
            }
            return PutObjectResponse.builder().build();
        });
    }

    private String getBodyContent(RequestBody body) {
        String stored;
        try (java.io.InputStream is = body.contentStreamProvider().newStream()) {
            stored = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read request body", e);
        }
        return stored;
    }

    private void mockGetListS3Objects(String tenantKey, String content) {
        mockGetListS3Objects(configPrefix + "tenants/" + tenantKey + "/", TEST_FILE_YAML, content);
    }

    private void mockGetListS3Objects(String contentPath, String contentFileName, String content) {
        // S3 paginator stub to return all objects under the prefix
        var objects = List.of(S3Object.builder().key(contentPath + contentFileName).build());
        var paginator = Mockito.mock(ListObjectsV2Iterable.class);
        when(paginator.contents()).thenReturn(objects::iterator);
        when(s3Client.listObjectsV2Paginator(ArgumentMatchers.<ListObjectsV2Request>argThat(request ->
                request != null && TEST_BUCKET_NAME.equals(request.bucket()) && contentPath.equals(request.prefix()))))
                .thenReturn(paginator);

        mockGetS3Object(contentPath, contentFileName, content, null);
    }

    private void mockGetS3Object(String contentPath, String contentFileName, String content, String version) {
        // mock getObject with ResponseTransformer.toBytes() using conditional Answer to avoid NPE
        var responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), content.getBytes(StandardCharsets.UTF_8));
        when(s3Client.getObject(
                any(GetObjectRequest.class),
                ArgumentMatchers.<ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>>>any())
        ).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            if (request != null && TEST_BUCKET_NAME.equals(request.bucket())
                    && Objects.equals(request.versionId(), version)
                    && (contentPath + contentFileName).equals(request.key())) {
                return responseBytes;
            }
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[0]);
        });
    }
}
