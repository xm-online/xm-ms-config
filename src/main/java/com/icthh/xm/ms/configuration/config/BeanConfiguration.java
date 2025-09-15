package com.icthh.xm.ms.configuration.config;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.icthh.xm.commons.config.client.service.TenantAliasService;
import com.icthh.xm.commons.config.domain.TenantAliasTree;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.request.spring.config.XmRequestContextConfiguration;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties.S3;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.DynamicConfigRepository;
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
import org.springframework.lang.Nullable;

@Slf4j
@Configuration
@Import(XmRequestContextConfiguration.class)
public class BeanConfiguration {

    public static final String TENANT_CONFIGURATION_LOCK = "tenant-configuration-lock";
    public static final String UPDATE_BY_COMMIT_LOCK = "update-by-commit-lock";
    public static final String UPDATE_IN_MEMORY = "in-memory-update-lock";

    private enum RepositoryMode {
        DYNAMIC, S3, GIT
    }

    /**
     * Creates and configures the {@link PersistenceConfigRepository} bean based on the application properties.
     * <p>
     * The repository implementation is selected according to the S3 configuration:
     * <ul>
     *   <li>If S3 is enabled and dynamic repository mode is enabled, a {@link DynamicConfigRepository} is created.</li>
     *   <li>If only S3 is enabled, a {@link S3Repository} is created.</li>
     *   <li>Otherwise, a {@link JGitRepository} is created.</li>
     * </ul>
     *
     * @param applicationProperties the application properties
     * @param lock the lock for tenant configuration
     * @param tenantContextHolder the tenant context holder
     * @param authenticationContextHolder the authentication context holder
     * @param requestContextHolder the request context holder
     * @param fileService the file service
     * @param s3Client the Amazon S3 client (nullable)
     * @return the configured {@link PersistenceConfigRepository}
     */
    @Bean
    public PersistenceConfigRepository configRepository(
            ApplicationProperties applicationProperties,
            @Qualifier(TENANT_CONFIGURATION_LOCK) Lock lock,
            TenantContextHolder tenantContextHolder,
            XmAuthenticationContextHolder authenticationContextHolder,
            XmRequestContextHolder requestContextHolder,
            FileService fileService,
            @Nullable AmazonS3 s3Client) {

        var mode = resolveRepositoryMode(applicationProperties);
        var s3Config = getS3Configuration(applicationProperties);

        return switch (mode) {
            case DYNAMIC -> createDynamicConfigRepository(s3Config, applicationProperties, lock,
                    tenantContextHolder, authenticationContextHolder, requestContextHolder, fileService, s3Client);
            case S3 -> createS3Repository(s3Config, s3Client);
            default -> createJGitRepository(applicationProperties, lock, tenantContextHolder,
                    authenticationContextHolder, requestContextHolder, fileService);
        };
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

    private static S3Repository createS3Repository(S3 s3, AmazonS3 s3Client) {
        log.info("Creating s3 repository");
        return new S3Repository(s3Client, s3.getBucket(), s3.getConfigPath());
    }

    private static JGitRepository createJGitRepository(ApplicationProperties applicationProperties, Lock lock,
            TenantContextHolder tenantContextHolder, XmAuthenticationContextHolder authenticationContextHolder,
            XmRequestContextHolder requestContextHolder, FileService fileService) {
        log.info("Creating jGit repository");
        return new JGitRepository(applicationProperties.getGit(), lock, tenantContextHolder,
                authenticationContextHolder, requestContextHolder, fileService);
    }

    private static DynamicConfigRepository createDynamicConfigRepository(S3 s3,
            ApplicationProperties applicationProperties, Lock lock, TenantContextHolder tenantContextHolder,
            XmAuthenticationContextHolder authenticationContextHolder, XmRequestContextHolder requestContextHolder,
            FileService fileService, AmazonS3 s3Client) {
        log.info("Creating dynamic config repository");
        return getDynamicConfigRepository(s3, applicationProperties, lock, tenantContextHolder,
                authenticationContextHolder,
                requestContextHolder, fileService, s3Client);
    }

    private static DynamicConfigRepository getDynamicConfigRepository(S3 s3,
            ApplicationProperties applicationProperties, Lock lock, TenantContextHolder tenantContextHolder,
            XmAuthenticationContextHolder authenticationContextHolder, XmRequestContextHolder requestContextHolder,
            FileService fileService, AmazonS3 s3Client) {
        var s3Repository = createS3Repository(s3, s3Client);
        var jGitRepository = createJGitRepository(applicationProperties, lock, tenantContextHolder,
                authenticationContextHolder, requestContextHolder, fileService);
        return new DynamicConfigRepository(jGitRepository, s3Repository, s3.getRules());
    }

    private static RepositoryMode resolveRepositoryMode(ApplicationProperties applicationProperties) {
        var s3Config = getS3Configuration(applicationProperties);
        boolean s3Enabled = Optional.ofNullable(s3Config.getEnabled()).orElse(false);
        boolean dynamicMode = Optional.ofNullable(s3Config.getDynamicRepositoryMode()).orElse(false);

        if (s3Enabled && dynamicMode) {
            return RepositoryMode.DYNAMIC;
        }
        if (s3Enabled) {
            return RepositoryMode.S3;
        }
        return RepositoryMode.GIT;
    }

    private static S3 getS3Configuration(ApplicationProperties applicationProperties) {
        return applicationProperties.getConfigRepository().getS3();
    }
}
