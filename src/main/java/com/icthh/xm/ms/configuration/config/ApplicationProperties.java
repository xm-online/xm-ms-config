package com.icthh.xm.ms.configuration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private List<String> tenantIgnoredPathList;

    private Map<String, String> hazelcast = new HashMap<>();

    @Getter
    @Setter
    public static class GitProperties {

        private String uri;
        private String login;
        private String password;
        private String branchName;
        private Integer maxWaitTimeSecond;
    }

}
