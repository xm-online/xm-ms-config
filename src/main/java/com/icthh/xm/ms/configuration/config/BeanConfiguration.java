package com.icthh.xm.ms.configuration.config;

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
import java.net.URI;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Slf4j
@Configuration
@Import(XmRequestContextConfiguration.class)
public class BeanConfiguration {

    public static final String TENANT_CONFIGURATION_LOCK = "tenant-configuration-lock";
    public static final String UPDATE_BY_COMMIT_LOCK = "update-by-commit-lock";
    public static final String UPDATE_IN_MEMORY = "in-memory-update-lock";

    /**
     * Creates the S3 Repository bean.
     *
     * @param applicationProperties the application properties
     * @param s3Client the Amazon S3 client
     * @return the configured {@link S3Repository}
     */
    @Bean
    @ConditionalOnExpression("'${application.config-repository.mode}'.equalsIgnoreCase('S3') || '${application.config-repository.mode}'.equalsIgnoreCase('DYNAMIC')")
    public PersistenceConfigRepository s3Repository(
            ApplicationProperties applicationProperties,
            S3Client s3Client) {

        log.info("Creating S3 repository bean");
        var s3Config = applicationProperties.getConfigRepository().getS3();
        return new S3Repository(s3Client, s3Config.getBucket(), s3Config.getConfigPath(), s3Config.getRules());
    }

    /**
     * Creates the Dynamic Config Repository bean.
     * Autowires list of all available repositories (s3Repository and gitRepository).
     * Note: dynamicRepository itself is not in the list yet since it's being created.
     *
     * @param repositories all available repository beans
     * @return the configured {@link DynamicConfigRepository}
     */
    @Bean
    @ConditionalOnExpression("'${application.config-repository.mode}'.equalsIgnoreCase('DYNAMIC')")
    public PersistenceConfigRepository dynamicRepository(List<PersistenceConfigRepository> repositories) {
        log.info("Creating dynamic config repository bean with {} base repositories", repositories.size());
        return new DynamicConfigRepository(repositories);
    }

    /**
     * Creates the Git Repository bean.
     *
     * @param applicationProperties the application properties
     * @param lock the lock for tenant configuration
     * @param tenantContextHolder the tenant context holder
     * @param authenticationContextHolder the authentication context holder
     * @param requestContextHolder the request context holder
     * @param fileService the file service
     * @return the configured {@link JGitRepository}
     */
    @Bean
    public PersistenceConfigRepository gitRepository(
            ApplicationProperties applicationProperties,
            @Qualifier(TENANT_CONFIGURATION_LOCK) Lock lock,
            TenantContextHolder tenantContextHolder,
            XmAuthenticationContextHolder authenticationContextHolder,
            XmRequestContextHolder requestContextHolder,
            FileService fileService) {

        log.info("Creating Git repository bean");
        return new JGitRepository(applicationProperties.getGit(), lock,
                tenantContextHolder, authenticationContextHolder, requestContextHolder, fileService);
    }

    /**
     * Selects and returns the appropriate {@link PersistenceConfigRepository} bean based on the configured mode.
     * <p>
     * The repository is selected from the available implementations according to application.config-repository.mode:
     * <ul>
     *   <li>DYNAMIC - Uses {@link DynamicConfigRepository} (combines S3 and Git with routing rules)</li>
     *   <li>S3 - Uses {@link S3Repository}</li>
     *   <li>GIT - Uses {@link JGitRepository} (default)</li>
     * </ul>
     *
     * @param applicationProperties the application properties
     * @param repositories all available repository beans
     * @return the selected {@link PersistenceConfigRepository}
     */
    @Bean
    public PersistenceConfigRepository configRepository(
            ApplicationProperties applicationProperties,
            List<PersistenceConfigRepository> repositories) {

        var mode = applicationProperties.getConfigRepository().getMode();
        log.info("Selecting config repository based on mode: {}", mode);

        return repositories.stream()
                .filter(repo -> repo.type().equalsIgnoreCase(mode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No repository found for mode: " + mode));
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
    @ConditionalOnExpression("'${application.config-repository.mode}'.equalsIgnoreCase('S3') || '${application.config-repository.mode}'.equalsIgnoreCase('DYNAMIC')")
    public S3Client s3Client(ApplicationProperties applicationProperties) {
        var s3Config = applicationProperties.getConfigRepository().getS3();
        var credentials = AwsBasicCredentials.create(s3Config.getAccessKey(), s3Config.getSecretKey());
        return S3Client.builder()
                .endpointOverride(URI.create(s3Config.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(s3Config.getRegion()))
                .serviceConfiguration(buildS3Configuration(s3Config))
                .build();
    }

    private static S3Configuration buildS3Configuration(S3 s3Config) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(s3Config.getPathStyleAccess())
                .build();
    }
}
