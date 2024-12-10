package com.icthh.xm.ms.configuration.service.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.icthh.xm.ms.configuration.config.Constants.TENANT_ENV_PATTERN;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_NAME;
import static com.icthh.xm.ms.configuration.config.Constants.TENANT_PREFIX;
import static java.lang.System.getenv;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.text.StringSubstitutor.replace;

@Slf4j
@Component
public class EnvConfigExternalizationFromFile implements TenantConfigurationProcessor {

    private final Map<String, String> environment;
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, TenantProfileEntry> tenantProfileCash = new ConcurrentHashMap<>();
    private final AntPathMatcher matcher = new AntPathMatcher();

    public EnvConfigExternalizationFromFile(ApplicationProperties applicationProperties) {
        Set<String> blacklist = applicationProperties.getEnvExternalizationBlacklist();
        this.environment = getenv().entrySet().stream()
            .filter(e -> !blacklist.contains(e.getKey()))
            .collect(Collectors.toMap(e -> "environment." + e.getKey(), Map.Entry::getValue));
    }

    @Override
    public boolean isSupported(Configuration configuration) {
        return true;
    }

    @SneakyThrows
    @Override
    public List<Configuration> processConfiguration(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage,
                                                    Set<Configuration> configToReprocess,
                                                    Map<String, Set<Configuration>> externalConfigs) {

        Map<String, String> tenantEnvs = new HashMap<>();
        Map<String, String> tenantProfile = buildTenantProfile(configuration, originalStorage);
        tenantEnvs.putAll(tenantProfile);
        tenantEnvs.putAll(environment);
        log.trace("Variables for replace {}", tenantEnvs);

        addToReprocessIfTenantProfile(configuration, originalStorage, configToReprocess);

        String originalContent = configuration.getContent();
        log.trace("Config before replace {}", originalContent);
        String content = originalContent.contains("${") ? replace(originalContent, tenantEnvs) : originalContent ;
        log.trace("Config after replace {}", content);
        if (!content.equals(originalContent)) {
            return singletonList(new Configuration(configuration.getPath(), content));
        } else {
            return emptyList();
        }
    }

    private void addToReprocessIfTenantProfile(Configuration configuration,
                                               Map<String, Configuration> originalStorage,
                                               Set<Configuration> configToReprocess) {
        String path = configuration.getPath();
        if (!path.contains(TENANT_PREFIX)) {
            return;
        }
        String tenantKey = matcher.extractUriTemplateVariables(TENANT_ENV_PATTERN, configuration.getPath()).get(TENANT_NAME);
        String tenantProfilePath = getTenantProfilePath(tenantKey);
        String tenantFolderPath = tenantFolderPath(tenantKey);

        if (path.equals(tenantProfilePath)) {
            List<Configuration> toReprocess = originalStorage.values().stream()
                .filter(config -> config.getPath().startsWith(tenantFolderPath))
                .filter(config -> !config.getPath().equals(tenantProfilePath))
                .collect(toList());
            configToReprocess.addAll(toReprocess);
        }
    }

    private Map<String, String> buildTenantProfile(Configuration configuration, Map<String, Configuration> originalStorage) {
        if (!configuration.getPath().contains(TENANT_PREFIX)) {
            log.trace("Config {} is not under tenant folder. It will not be processed by externalization.", configuration.getPath());
            return emptyMap();
        }

        String tenantKey = matcher.extractUriTemplateVariables(TENANT_ENV_PATTERN, configuration.getPath()).get(TENANT_NAME);
        String tenantProfilePath = getTenantProfilePath(tenantKey);
        Configuration tenantProfileConfig = originalStorage.get(tenantProfilePath);
        if (tenantProfileConfig == null) {
            return emptyMap();
        }

        String tenantProfileContent = tenantProfileConfig.getContent();
        TenantProfileEntry tenantProfileEntry = tenantProfileCash.computeIfAbsent(tenantProfilePath,
            key -> new TenantProfileEntry(tenantProfileContent, getConfigMap(tenantProfileContent)));
        return getMap(tenantProfilePath, tenantProfileEntry, tenantProfileContent);
    }

    private static String getTenantProfilePath(String tenantKey) {
        return tenantFolderPath(tenantKey) + "/tenant-profile.yml";
    }

    private static String tenantFolderPath(String tenantKey) {
        return TENANT_PREFIX + tenantKey;
    }

    private Map<String, String> getMap(String tenantEnvValuePath, TenantProfileEntry tenantProfileEntry, String tenantProfileContent) {
        Map<String, String> configMap;
        if (tenantProfileEntry.tenantProfileContent.equals(tenantProfileContent)) {
            configMap = tenantProfileEntry.tenantProfile;
        } else {
            configMap = getConfigMap(tenantProfileContent);
            tenantProfileCash.put(tenantEnvValuePath, new TenantProfileEntry(tenantProfileContent, configMap));
        }
        return configMap;
    }

    @SneakyThrows
    private Map<String, String> getConfigMap(String tenantProfileBody) {
        Map<String, String> tenantProfile = new ConcurrentHashMap<>();
        addKeys("", objectMapper.readTree(tenantProfileBody), tenantProfile);
        return tenantProfile;
    }

    private void addKeys(String currentPath, JsonNode jsonNode, Map<String, String> map) {
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
            String pathPrefix = currentPath.isEmpty() ? "" : currentPath + ".";

            while (iter.hasNext()) {
                Map.Entry<String, JsonNode> entry = iter.next();
                addKeys(pathPrefix + entry.getKey(), entry.getValue(), map);
            }
        } else if (jsonNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) jsonNode;
            for (int i = 0; i < arrayNode.size(); i++) {
                addKeys(currentPath + "[" + i + "]", arrayNode.get(i), map);
            }
        } else if (jsonNode.isValueNode()) {
            ValueNode valueNode = (ValueNode) jsonNode;
            map.put(currentPath, valueNode.asText());
        }
    }

    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class TenantProfileEntry {
        private final String tenantProfileContent;
        private final Map<String, String> tenantProfile;
    }
}
