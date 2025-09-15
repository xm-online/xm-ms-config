package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.S3Rules;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicConfigRepository implements PersistenceConfigRepository {

    private static final ConfigVersion S3_VERSION = new ConfigVersion("s3");

    private final PersistenceConfigRepository jGitRepository;
    private final PersistenceConfigRepository s3Repository;
    private final S3Rules s3Rules;

    public DynamicConfigRepository(PersistenceConfigRepository jGitRepository, PersistenceConfigRepository s3Repository, S3Rules s3Rules) {
        this.jGitRepository = jGitRepository;
        this.s3Repository = s3Repository;
        this.s3Rules = s3Rules;
    }

    @Override
    public boolean hasVersion(ConfigVersion version) {
        return jGitRepository.hasVersion(version);
    }

    @Override
    public ConfigurationList findAll() {
        var jGitConfigurationList = jGitRepository.findAll();
        var jGitData = jGitConfigurationList.getData();
        var s3ConfigurationList = s3Repository.findAll();
        var s3Data = s3ConfigurationList.getData();
        var jGitVersion = jGitConfigurationList.getVersion();
        return getConfigurationList(s3Data, jGitData, jGitVersion);
    }

    @Override
    public ConfigurationList findAllInTenant(String tenantKey) {
        var jGitConfigurationList = jGitRepository.findAllInTenant(tenantKey);
        var jGitData = jGitConfigurationList.getData();
        var s3ConfigurationList = s3Repository.findAllInTenant(tenantKey);
        var s3Data = s3ConfigurationList.getData();
        var jGitVersion = jGitConfigurationList.getVersion();
        return getConfigurationList(s3Data, jGitData, jGitVersion);
    }

    @Override
    public ConfigurationList findAllInTenants(Set<String> tenants) {
        var jGitConfigurationList = jGitRepository.findAllInTenants(tenants);
        var jGitData = jGitConfigurationList.getData();
        var s3ConfigurationList = s3Repository.findAllInTenants(tenants);
        var s3Data = s3ConfigurationList.getData();
        var jGitVersion = jGitConfigurationList.getVersion();
        return getConfigurationList(s3Data, jGitData, jGitVersion);
    }

    @Override
    public ConfigurationItem find(String path) {
        return isS3Routed(path)
                ? s3Repository.find(path)
                : jGitRepository.find(path);
    }

    @Override
    public Configuration find(String path, ConfigVersion version) {
        return isS3Routed(path)
                ? s3Repository.find(path, version)
                : jGitRepository.find(path, version);
    }

    @Override
    public ConfigVersion save(Configuration configuration) {
        return isS3Routed(configuration.getPath())
                ? s3Repository.save(configuration)
                : jGitRepository.save(configuration);
    }

    @Override
    public ConfigVersion saveAll(List<Configuration> configurations, Map<String, String> configHashes) {
        var s3Configs = findS3Config(configurations);
        if (!s3Configs.isEmpty()) {
            s3Repository.saveAll(s3Configs, configHashes);
        }

        var version = S3_VERSION;
        var gitConfigs = findGitConfig(configurations);
        if (!gitConfigs.isEmpty()) {
            version = jGitRepository.saveAll(gitConfigs, configHashes);
        }

        return version;
    }

    private List<Configuration> findGitConfig(List<Configuration> configurations) {
        return configurations.stream()
                .filter(config -> !isS3Routed(config.getPath()))
                .toList();
    }

    private List<Configuration> findS3Config(List<Configuration> configurations) {
        return configurations.stream()
                .filter(config -> isS3Routed(config.getPath()))
                .toList();
    }

    @Override
    public ConfigVersion setRepositoryState(List<Configuration> configurations) {
        var s3Configs = findS3Config(configurations);
        if (!s3Configs.isEmpty()) {
            s3Repository.setRepositoryState(s3Configs);
        }

        var version = S3_VERSION;
        var gitConfigs = findGitConfig(configurations);
        if (!gitConfigs.isEmpty()) {
            version = jGitRepository.setRepositoryState(gitConfigs);
        }

        return version;
    }

    @Override
    public ConfigVersion deleteAll(List<String> paths) {
        var s3Paths = paths.stream().filter(this::isS3Routed).toList();
        if (!s3Paths.isEmpty()) {
            s3Repository.deleteAll(s3Paths);
        }

        var version = S3_VERSION;
        var gitPaths = paths.stream().filter(path -> !isS3Routed(path)).toList();
        if (!gitPaths.isEmpty()) {
            version = jGitRepository.deleteAll(gitPaths);
        }
        return version;
    }

    @Override
    public void recloneConfiguration() {
        jGitRepository.recloneConfiguration();
    }

    @Override
    public ConfigVersion getCurrentVersion() {
        return jGitRepository.getCurrentVersion();
    }

    private ConfigurationList getConfigurationList(List<Configuration> s3Data, List<Configuration> jGitData,
            ConfigVersion jGitVersion) {
        var s3Map = s3Data.stream().collect(Collectors.toMap(Configuration::getPath, c -> c));
        var gitMap = jGitData.stream().collect(Collectors.toMap(Configuration::getPath, c -> c));

        var allPaths = Stream.concat(
                        jGitData.stream().map(Configuration::getPath),
                        s3Data.stream().map(Configuration::getPath)
                )
                .distinct()
                .toList();

        var mergedList = allPaths.stream()
                .map(path -> isS3Routed(path) ? s3Map.get(path) : gitMap.get(path))
                .filter(java.util.Objects::nonNull)
                .toList();

        return new ConfigurationList(jGitVersion, mergedList);
    }

    private boolean isS3Routed(String path) {
        boolean included = s3Rules.getIncludePaths().stream().anyMatch(pattern -> pathMatches(pattern, path));
        boolean excluded = s3Rules.getExcludePaths().stream().anyMatch(pattern -> pathMatches(pattern, path));
        return included && !excluded;
    }

    private boolean pathMatches(String pattern, String path) {
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return path.startsWith(prefix);
        }
        return path.equals(pattern);
    }
}
