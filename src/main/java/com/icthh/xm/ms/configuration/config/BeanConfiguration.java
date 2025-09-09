package com.icthh.xm.ms.configuration.config;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.client.service.TenantAliasService;
import com.icthh.xm.commons.config.domain.TenantAliasTree;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.request.spring.config.XmRequestContextConfiguration;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.ConfigRepository;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.S3;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.JGitRepository;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorageExcludeConfigDecorator;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorageImpl;
import com.icthh.xm.ms.configuration.repository.impl.S3Repository;
import com.icthh.xm.ms.configuration.service.FileService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeStorage;
import com.icthh.xm.ms.configuration.service.processors.TenantConfigurationProcessor;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Configuration
@Import(XmRequestContextConfiguration.class)
public class BeanConfiguration {

    public static final String TENANT_CONFIGURATION_LOCK = "tenant-configuration-lock";
    public static final String UPDATE_BY_COMMIT_LOCK = "update-by-commit-lock";
    public static final String UPDATE_IN_MEMORY = "in-memory-update-lock";

    @Bean
    public PersistenceConfigRepository configRepository(
            ApplicationProperties applicationProperties,
            @Qualifier(TENANT_CONFIGURATION_LOCK) Lock lock,
            TenantContextHolder tenantContextHolder,
            XmAuthenticationContextHolder authenticationContextHolder,
            XmRequestContextHolder requestContextHolder,
            FileService fileService,
            AmazonS3 s3Client) {

        var s3Configuration = getS3Configuration(applicationProperties);
        boolean s3Enabled = s3Configuration
                .map(S3::getEnabled)
                .orElse(false);

        if (s3Enabled) {
            var s3 = s3Configuration.get();
            return new S3Repository(s3Client, s3.getBucket(), s3.getConfigPath());
        } else {
            return new JGitRepository(applicationProperties.getGit(), lock, tenantContextHolder,
                    authenticationContextHolder, requestContextHolder, fileService);
        }
    }

    private static Optional<S3> getS3Configuration(ApplicationProperties applicationProperties) {
        return Optional.of(applicationProperties)
                .map(ApplicationProperties::getConfigRepository)
                .map(ConfigRepository::getS3);
    }

    @Bean
    @Qualifier(TENANT_CONFIGURATION_LOCK)
    public Lock gitRepositoryLock() {
        return new ReentrantLock();
    }

    @Bean
    @Qualifier(UPDATE_BY_COMMIT_LOCK)
    public Lock updateByCommitLock() {
        return new ReentrantLock();
    }

    @Bean
    @Qualifier(UPDATE_IN_MEMORY)
    public Lock inmemoryUpdateLock() {
        return new ReentrantLock();
    }

    @Bean
    public MemoryConfigStorage memoryConfigStorage(List<TenantConfigurationProcessor> tenantConfigurationProcessors,
                                                   TenantAliasTreeStorage tenantAliasTreeStorage,
                                                   ApplicationProperties applicationProperties,
                                                   @Qualifier(UPDATE_IN_MEMORY)
                                                   Lock lock) {

        return new MemoryConfigStorageExcludeConfigDecorator(
            new MemoryConfigStorageImpl(
                tenantConfigurationProcessors,
                tenantAliasTreeStorage,
                applicationProperties,
                lock
            ),
            applicationProperties
        );
    }

    @Bean
    public TenantAliasService tenantAliasService(TenantAliasTreeStorage tenantAliasTreeStorage) {
        return new TenantAliasService() {
            @Override
            public TenantAliasTree getTenantAliasTree() {
                return tenantAliasTreeStorage.getTenantAliasTree();
            }

            @Override
            public void onRefresh(String config) {
                // do noting
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "application.config-repository.s3", name = "enabled", havingValue = "true")
    public AmazonS3 amazonS3(ApplicationProperties applicationProperties) {
        var s3Config = applicationProperties.getConfigRepository().getS3();
        AWSCredentials credentials = new BasicAWSCredentials(s3Config.getAccessKey(), s3Config.getSecretKey());
        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(s3Config.getEndpoint(), s3Config.getRegion()))
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withPathStyleAccessEnabled(s3Config.getPathStyleAccess())
                .build();
    }
}
