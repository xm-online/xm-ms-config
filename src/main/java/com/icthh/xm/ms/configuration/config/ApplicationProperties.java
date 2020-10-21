package com.icthh.xm.ms.configuration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

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

    private List<String> tenantIgnoredPathList = Collections.emptyList();
    private boolean kafkaEnabled;
    private String kafkaSystemQueue;
    private Integer kafkaMetadataMaxAge;

    @Getter
    @Setter
    public static class GitProperties {

        private String uri;
        private String login;
        private String password;
        private String branchName;
        private Integer maxWaitTimeSecond = 30;
        private SshProperties ssh = new SshProperties();

        @Getter
        @Setter
        public static class SshProperties {
            private boolean enabled;
            private String privateKey;
            private String passPhrase;
        }
    }

    @Getter
    @Setter
    private static class Retry {

        private int maxAttempts;
        private long delay;
        private int multiplier;
    }
}
