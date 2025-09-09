package com.icthh.xm.ms.configuration.config;

import static java.util.Collections.emptySet;

import com.icthh.xm.commons.lep.TenantScriptStorage;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to JHipster.
 * <p>
 * Properties are configured in the application.yml file.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
@Getter
@Setter
public class ApplicationProperties {

    private GitProperties git;
    private final Retry retry = new Retry();
    private final Retry configQueueRetry = new Retry();

    private Boolean adminApiRestrictionEnabled;
    private List<String> superTenantsList;
    private List<String> tenantIgnoredPathList = Collections.emptyList();
    private List<String> binaryFileTypes = Collections.emptyList();
    private boolean kafkaEnabled;
    private String kafkaSystemQueue;
    private Integer kafkaMetadataMaxAge;
    private boolean updateConfigAvailable = true;

    private Boolean envConfigExternalizationEnabled;
    private Integer updateConfigWaitTimeSecond = 120;
    private Integer versionCacheMaxSize = 100;
    private Boolean sendRefreshOnStartup;
    private Integer jwkUpdateDebounceSeconds = 30;

    private Set<String> envExternalizationBlacklist = emptySet();

    @Getter
    @Setter
    public static class GitProperties {

        private String uri;
        private String login;
        private String password;
        private String branchName;
        private Integer depth = -1;
        private Integer maxWaitTimeSecond = 30;
        private Boolean cloneRepositoryOnUpdate = false;
        private SshProperties ssh = new SshProperties();

        @Getter
        @Setter
        public static class SshProperties {
            private boolean enabled;
            private String privateKey;
            private String passPhrase;
            private boolean acceptUnknownHost;
        }
    }

    @Getter
    @Setter
    private static class Retry {

        private int maxAttempts;
        private long delay;
        private int multiplier;
    }

    private LepProperties lep;

    @Getter
    @Setter
    public static class LepProperties {
        private TenantScriptStorage tenantScriptStorage;
        private Boolean processorEnabled;
    }

    private ConfigRepository configRepository = new ConfigRepository();

    @Getter
    @Setter
    public static class ConfigRepository {

        private S3 s3 = new S3();
    }

    @Getter
    @Setter
    public static class S3 {

        private Boolean enabled;
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String region;
        private String bucket;
        private String configPath;
        private Boolean pathStyleAccess;
    }

    private List<String> excludeConfigPatterns;
    private Boolean roleNameProcessorDisabled;

}
