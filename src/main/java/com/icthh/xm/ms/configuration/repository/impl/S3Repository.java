package com.icthh.xm.ms.configuration.repository.impl;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.common.collect.Lists;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
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

    private ConfigurationList readFromDirectory(String prefix) {
        var request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();
        var response = s3Client.listObjectsV2Paginator(request);
        var configs = response.contents().stream()
                .filter(o -> !o.key().endsWith("/"))
                .map(this::findConfiguration)
                .toList();
        return new ConfigurationList(S3_VERSION, configs);
    }

    private Configuration findConfiguration(S3Object s3Object) {
        var key = s3Object.key();
        var path = (configPath != null && key.startsWith(configPath)) ? key.substring(configPath.length()) : key;
        var s3File = readFile(path, null);
        return new Configuration(path, s3File.content);
    }

    private S3File readFile(String path, String version) {
        var key = resolveKey(path);
        var request = GetObjectRequest.builder()
                .bucket(bucketName)
                .versionId(version)
                .key(key)
                .build();
        try {
            var response = s3Client.getObject(request, ResponseTransformer.toBytes());
            var content = response.asUtf8String();
            version = response.response().versionId();
            return new S3File(version, content);
        } catch (Exception e) {
            log.warn("Could not read S3 object: {}", key, e);
            return new S3File(version, null);
        }
    }

    @Override
    public ConfigurationList findAllInTenant(String tenantKey) {
        return readFromDirectory(configPrefix + "tenants/" + tenantKey + "/");
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
        var s3File = readFile(path, null);
        return new ConfigurationItem(buildConfigVersion(s3File.version), new Configuration(path, s3File.content));
    }

    private static ConfigVersion buildConfigVersion(String s3Version) {
        return Optional.ofNullable(s3Version).map(ConfigVersion::new).orElse(S3_VERSION);
    }

    @Override
    public Configuration find(String path, ConfigVersion version) {
        var s3File = readFile(path, version.getMainVersion());
        return new Configuration(path, s3File.content);
    }

    @Override
    public ConfigVersion save(Configuration configuration) {
        var path = configuration.getPath();
        var key = resolveKey(path);
        var request = PutObjectRequest.builder().bucket(bucketName).key(key).build();
        var content = Optional.ofNullable(configuration.getContent()).orElse(Strings.EMPTY);
        var requestBody = RequestBody.fromString(content, StandardCharsets.UTF_8);
        var response = s3Client.putObject(request, requestBody);
        return buildConfigVersion(response.versionId());
    }

    @Override
    public ConfigVersion saveAll(List<Configuration> configurations, Map<String, String> configHashes) {
        configurations.forEach(configuration -> save(configuration, configHashes.get(configuration.getPath())));
        return S3_VERSION;
    }

    private void save(Configuration configuration, String oldConfigHash) {
        if (isBlank(oldConfigHash)) {
            return;
        }
        assertConfigHash(configuration, oldConfigHash);
        save(configuration);
    }

    private void assertConfigHash(Configuration configuration, String oldConfigHash) {
        if (isBlank(oldConfigHash)) {
            return;
        }
        var s3File = readFile(configuration.getPath(), null);
        String expectedOldConfigHash = sha1Hex(s3File.content);
        log.debug("Expected hash {}, actual hash {}", expectedOldConfigHash, oldConfigHash);
        if (!expectedOldConfigHash.equals(oldConfigHash)) {
            throw new ConcurrentConfigModificationException();
        }
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

    private record S3File(String version, String content) {

    }
}
