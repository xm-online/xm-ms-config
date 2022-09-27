package com.icthh.xm.ms.configuration.service.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.getenv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Slf4j
@Component
public class EnvConfigExternalizationFromFile implements PrivateConfigurationProcessor {

    private final Map<String, String> environment;
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, TenantProfileEntry> tenantProfileCash = new ConcurrentHashMap<>();
    private final AntPathMatcher matcher = new AntPathMatcher();

    private static final String TENANT_NAME = "tenantName";
    public static final String TENANT_PREFIX = "/config/tenants/";
    private final String TENANT_ENV_PATTERN = TENANT_PREFIX + "{" + TENANT_NAME + "}/**";

    public EnvConfigExternalizationFromFile() {
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
        String configurationPath = configuration.getPath();
        if (!configurationPath.contains(TENANT_PREFIX)) {
            return emptyList();
        }
        String tenantKey = matcher.extractUriTemplateVariables(TENANT_ENV_PATTERN, configuration.getPath()).get(TENANT_NAME);
        String tenantEnvValuePath = TENANT_PREFIX + tenantKey + "/tenant-profile.yml";
        Configuration tenantEnvValue = originalStorage.get(tenantEnvValuePath);

        if (tenantEnvValue == null) {
            return emptyList();
        }
        Map<String, String> tenantEnvs = new ConcurrentHashMap<>();
        TenantProfileEntry tenantProfileEntry = tenantProfileCash.computeIfAbsent(tenantEnvValuePath,
            key -> new TenantProfileEntry(tenantEnvValue.getContent(), getConfigMap(tenantEnvValue)));
        Map<String, String> configMap = getMap(tenantEnvValuePath, tenantEnvValue, tenantProfileEntry);

        tenantEnvs.putAll(configMap);
        tenantEnvs.putAll(environment);
        String content = StrSubstitutor.replace(configuration.getContent(), tenantEnvs);
        return singletonList(new Configuration(configuration.getPath(), content));
    }

    private Map<String, String> getMap(String tenantEnvValuePath, Configuration tenantEnvValue, TenantProfileEntry tenantProfileEntry) {
        Map<String, String> configMap;
        if (tenantProfileEntry.tenantProfileContent.equals(tenantEnvValue.getContent())) {
            configMap = tenantProfileEntry.tenantProfile;
        } else {
            configMap = getConfigMap(tenantEnvValue);
            tenantProfileCash.put(tenantEnvValuePath, new TenantProfileEntry(tenantEnvValue.getContent(), configMap));
        }
        return configMap;
    }

    @SneakyThrows
    private Map<String, String> getConfigMap(Configuration tenantEnvValue) {
        Map<String, String> tenantProfile = new ConcurrentHashMap<>();
        addKeys("", objectMapper.readTree(tenantEnvValue.getContent()), tenantProfile);
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
