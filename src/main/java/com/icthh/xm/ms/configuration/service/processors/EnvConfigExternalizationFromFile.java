package com.icthh.xm.ms.configuration.service.processors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.text.StrSubstitutor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Boolean.TRUE;
import static java.lang.System.getenv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

@Slf4j
@Component
@Order(LOWEST_PRECEDENCE)
public class EnvConfigExternalizationFromFile implements PrivateConfigurationProcessor {

    private final Map<String, String> environment;
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, TenantProfileEntry> tenantProfileCash = new ConcurrentHashMap<>();
    private final AntPathMatcher matcher = new AntPathMatcher();

    private static final String TENANT_NAME = "tenantName";
    public static final String TENANT_PREFIX = "/config/tenants/";
    private final String TENANT_ENV_PATTERN = TENANT_PREFIX + "{" + TENANT_NAME + "}/**";

    public EnvConfigExternalizationFromFile(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
        Map<String, String> env = new HashMap<>();
        getenv().forEach((key, value) -> env.put("environment." + key, value));
        this.environment = env;
    }

    @Override
    public boolean isSupported(Configuration configuration) {
        return true;
    }

    @SneakyThrows
    @Override
    public List<Configuration> processConfiguration(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage) {
        if (TRUE.equals(applicationProperties.getEnvConfigExternalizationEnabled())) {
            String configurationPath = configuration.getPath();
            if (configurationPath.contains(TENANT_PREFIX)) {
                String tenantKey = matcher.extractUriTemplateVariables(TENANT_ENV_PATTERN, configuration.getPath()).get(TENANT_NAME);
                String tenantEnvValuePath = TENANT_PREFIX + tenantKey + "/tenant-env-value.yml";
                Configuration tenantEnvValue = originalStorage.get(tenantEnvValuePath);

                if (tenantEnvValue != null) {
                    Map<String, String> tenantEnvs = new ConcurrentHashMap<>();
                    TenantProfileEntry tenantProfileEntry = tenantProfileCash.computeIfAbsent(tenantEnvValuePath,
                        key -> new TenantProfileEntry(tenantEnvValue.getContent(), getConfigMap(tenantEnvValue)));
                    Map<String, String> configMap;

                    if (tenantProfileEntry.tenantProfileContent.equals(tenantEnvValue.getContent())) {
                        configMap = tenantProfileEntry.tenantProfile;
                    } else {
                        configMap = getConfigMap(tenantEnvValue);
                        tenantProfileCash.put(tenantEnvValuePath, new TenantProfileEntry(tenantEnvValue.getContent(), configMap));
                    }
                    tenantEnvs.putAll(configMap);
                    tenantEnvs.putAll(environment);
                    String content = StrSubstitutor.replace(configuration.getContent(), tenantEnvs);
                    return singletonList(new Configuration(configuration.getPath(), content));
                }
            }
        }
        return emptyList();
    }

    @SneakyThrows
    private Map<String, String> getConfigMap(Configuration tenantEnvValue) {
        return objectMapper.readValue(tenantEnvValue.getContent(),
            new TypeReference<Map<String, String>>() {
        });
    }

    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class TenantProfileEntry {
        private final String tenantProfileContent;
        private final Map<String, String> tenantProfile;
    }
}
