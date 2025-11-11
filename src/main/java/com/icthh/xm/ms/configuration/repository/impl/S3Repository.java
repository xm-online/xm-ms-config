package com.icthh.xm.ms.configuration.repository.impl;

import com.google.common.collect.Lists;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
public class S3Repository implements PersistenceConfigRepository {

    private static final ConfigVersion S3_VERSION = new ConfigVersion("s3");

    private final String bucketName;
    private final String configPath;
    private final S3Client s3Client;
    private final String configPrefix;

    public S3Repository(S3Client s3Client, String bucketName, String configPath) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.configPath = configPath;
        this.configPrefix = resolveConfigPrefix(configPath);
        log.info("S3 bucket to use: {}, config path: {}", bucketName, configPath);
    }

    private static String resolveConfigPrefix(String configPath) {
        return configPath != null ? configPath + "/config/" : "config/";
    }

    @Override
    public boolean hasVersion(ConfigVersion version) {
        // S3 does not have commit versions, always return true for now
        return true;
    }

    @Override
    public ConfigurationList findAll() {
        return readFromDirectory(configPrefix);
    }

    @Override
    public ConfigurationList findAllInTenant(String tenantKey) {
        return readFromDirectory(configPrefix + "tenants/" + tenantKey + "/");
    }

    private ConfigurationList readFromDirectory(String prefix) {
        var request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();
        var response = s3Client.listObjectsV2Paginator(request);
        var configs = response.contents().stream()
                .filter(o -> !o.key().endsWith("/"))
                .map(this::toConfiguration)
                .toList();
        return new ConfigurationList(S3_VERSION, configs);
    }

    private Configuration toConfiguration(S3Object s3Object) {
        var key = s3Object.key();
        var path = (configPath != null && key.startsWith(configPath)) ? key.substring(configPath.length()) : key;
        var content = readFileContent(key);
        return new Configuration(path, content);
    }

    @Override
    public ConfigurationList findAllInTenants(Set<String> tenants) {
        var configs = tenants.stream()
                .flatMap(tenant -> findAllInTenant(tenant).getData().stream())
                .toList();
        return new ConfigurationList(S3_VERSION, configs);
    }

    @Override
    public ConfigurationItem find(String path) {
        var key = resolveKey(path);
        var content = readFileContent(key);
        return new ConfigurationItem(S3_VERSION, new Configuration(path, content));
    }

    @Override
    public Configuration find(String path, ConfigVersion version) {
        // S3 does not support versioning by default, so ignore version param
        return Optional.of(find(path))
                .map(ConfigurationItem::getData)
                .orElse(null);
    }

    private String readFileContent(String key) {
        var request = GetObjectRequest.builder().bucket(bucketName).key(key).build();
        try {
            var response = s3Client.getObject(request, ResponseTransformer.toBytes());
            return response.asUtf8String();
        } catch (Exception e) {
            log.warn("Could not read S3 object: {}", key, e);
            return null;
        }
    }

    @Override
    public ConfigVersion save(Configuration configuration) {
        var path = configuration.getPath();
        var key = resolveKey(path);
        var request = PutObjectRequest.builder().bucket(bucketName).key(key).build();
        var content = Optional.ofNullable(configuration.getContent()).orElse(Strings.EMPTY);
        var requestBody = RequestBody.fromString(content, StandardCharsets.UTF_8);
        s3Client.putObject(request, requestBody);
        return S3_VERSION;
    }

    @Override
    public ConfigVersion saveAll(List<Configuration> configurations, Map<String, String> configHashes) {
        configurations.forEach(this::save);
        return S3_VERSION;
    }

    @Override
    public ConfigVersion setRepositoryState(List<Configuration> configurations) {
        // Delete all objects under config/ and re-upload
        var requestList = ListObjectsV2Request.builder().bucket(bucketName).prefix(configPrefix).build();
        var responseList = s3Client.listObjectsV2Paginator(requestList);
        var keys = responseList.contents().stream()
                .map(S3Object::key)
                .toList();
        deleteByKeys(keys);
        configurations.forEach(this::save);
        return S3_VERSION;
    }

    @Override
    public ConfigVersion deleteAll(List<String> paths) {
        List<String> normalized = paths.stream()
                .filter(Objects::nonNull)
                .map(this::resolveKey)
                .toList();
        deleteByKeys(normalized);
        return S3_VERSION;
    }

    private void deleteByKeys(List<String> keys) {
        for (List<String> batch : Lists.partition(keys, 1000)) {
            var request = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder()
                            .objects(
                                    batch.stream()
                                            .map(k -> ObjectIdentifier.builder().key(k).build())
                                            .toList()
                            )
                            .build())
                    .build();

            s3Client.deleteObjects(request);
        }
    }

    private String resolveKey(String path) {
        path = path.startsWith("/") ? path.substring(1) : path;
        return configPath != null ? configPath + "/" + path : path;
    }

    @Override
    public void recloneConfiguration() {
        // No-op for S3
    }

    @Override
    public ConfigVersion getCurrentVersion() {
        // S3 does not have commit versions, return pseudo-version
        return S3_VERSION;
    }
}
