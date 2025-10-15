package com.icthh.xm.ms.configuration.repository.impl;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
public class S3Repository implements PersistenceConfigRepository {

    private static final ConfigVersion S3_VERSION = new ConfigVersion("s3");

    private final String bucketName;
    private final String configPath;
    private final AmazonS3 s3Client;
    private final String configPrefix;

    public S3Repository(AmazonS3 s3Client, String bucketName, String configPath) {
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
        var listing = s3Client.listObjects(bucketName, prefix);
        var configs = listing.getObjectSummaries().stream()
                .filter(summary -> !summary.getKey().endsWith("/"))
                .map(this::toConfiguration)
                .toList();
        return new ConfigurationList(S3_VERSION, configs);
    }

    private Configuration toConfiguration(S3ObjectSummary summary) {
        var key = summary.getKey();
        var path = (configPath!= null && key.startsWith(configPath)) ? key.substring(configPath.length()) : key;
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
        try (S3Object s3Object = s3Client.getObject(bucketName, key)) {
            return IOUtils.toString(s3Object.getObjectContent(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not read S3 object: {}", key, e);
            return null;
        }
    }

    @Override
    public ConfigVersion save(Configuration configuration) {
        var path = configuration.getPath();
        var key = resolveKey(path);
        s3Client.putObject(bucketName, key, configuration.getContent());
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
        ObjectListing listing = s3Client.listObjects(bucketName, configPrefix);
        listing.getObjectSummaries().forEach(summary ->
                s3Client.deleteObject(bucketName, resolveKey(summary.getKey()))
        );
        configurations.forEach(this::save);
        return S3_VERSION;
    }

    @Override
    public ConfigVersion deleteAll(List<String> paths) {
        paths.forEach(path -> {
            String key = resolveKey(path);
            s3Client.deleteObject(bucketName, key);
        });
        return S3_VERSION;
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
