package com.icthh.xm.ms.configuration.service.processors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.icthh.xm.commons.config.client.service.TenantConfigService.DEFAULT_TENANT_CONFIG_PATTERN;
import static java.lang.System.getenv;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

@Slf4j
@Component
@Order(LOWEST_PRECEDENCE)
public class TenantConfigExternalization implements TenantConfigurationProcessor {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private static final String TENANT_NAME = "tenantName";
    private final Map<String, String> env = getenv();

    @Override
    public boolean isSupported(Configuration configuration) {
        return matcher.match(DEFAULT_TENANT_CONFIG_PATTERN, configuration.getPath());
    }

    @Override
    @SneakyThrows
    public List<Configuration> processConfiguration(Configuration configuration,
                                                    Map<String, Configuration> originalStorage,
                                                    Map<String, Configuration> targetStorage,
                                                    Set<Configuration> configToReprocess,
                                                    Map<String, Set<Configuration>> externalConfigs) {
        String tenant = matcher.extractUriTemplateVariables(DEFAULT_TENANT_CONFIG_PATTERN, configuration.getPath()).get(TENANT_NAME);
        String content = targetStorage.getOrDefault(configuration.getPath(), configuration).getContent();
        Map<String, Object> configMap = mapper.readValue(content, new TypeReference<Map<String, Object>>() {
        });

        if (configMap != null && isEnvPresent(tenant + "_")) {
            processConfigMap(tenant, configMap);
        } else {
            return emptyList();
        }

        return singletonList(new Configuration(configuration.getPath(), mapper.writeValueAsString(configMap)));
    }

    @SuppressWarnings("unchecked")
    private void processConfigMap(String path, Map<String, Object> configMap) {
        for (Map.Entry<String, Object> entry : configMap.entrySet()) {
            String key = entry.getKey();
            Object oldValue = entry.getValue();
            String envVarKey = path + "_" + key;
            if (isSimpleValueType(oldValue) && isEnvExactlyPresent(envVarKey)) {
                Object envValue = getEnv(envVarKey);
                log.info("{} it's simple type |{}|, and override by env variable |{}|", envVarKey, oldValue, envValue);
                entry.setValue(envValue);
                continue;
            }
            if (!isEnvPresent(envVarKey + "_")) {
                log.trace("{} and not overridden by env variable", envVarKey);
                continue;
            }
            if (oldValue == null) {
                log.trace("{} is null, and not overridden by env variable", envVarKey);
                continue;
            }
            if (oldValue instanceof Map) {
                processConfigMap(envVarKey, (Map<String, Object>) oldValue);
                continue;
            } else if (isEnvExactlyPresent(envVarKey)) {
                log.error("Unsupported type of config value {}:{}", envVarKey, oldValue.getClass());
            }

            log.warn("Env variables not overridden any configs {}", getEnvKeysStartBy(envVarKey));
        }
    }

    private List<String> getEnvKeysStartBy(String path) {
        return env.keySet().stream().filter(it -> checkPathVariants(path, it::startsWith)).collect(toList());
    }

    private boolean isEnvExactlyPresent(String path) {
        return env.keySet().stream().anyMatch(it -> checkPathVariants(path, env::containsKey));
    }

    private boolean isSimpleValueType(Object object) {
        return object == null || BeanUtils.isSimpleValueType(object.getClass());
    }

    private boolean isEnvPresent(String path) {
        return env.keySet().stream().anyMatch(it -> checkPathVariants(path, it::startsWith));
    }

    private boolean checkPathVariants(String path, Predicate<String> predicate) {
        return getPathVariants(path).stream().anyMatch(predicate);
    }

    private List<String> getPathVariants(String path) {
        String upperCasePath = path.toUpperCase();
        return asList(path, path.replaceAll("-", "_"), upperCasePath.replaceAll("-", "_"), upperCasePath.replaceAll("-", ""));
    }

    private Object getEnv(String path) {

        String envVariable = getPathVariants(path).stream().filter(env::containsKey).map(env::get).findFirst().orElse("");

        if (StringUtils.isBlank(envVariable)) {
            return envVariable;
        }

        if (!isNumber(envVariable)) {
            return envVariable;
        }

        try {
            Number number = NumberFormat.getInstance().parse(envVariable);
            if (number instanceof Long && number.longValue() < Integer.MAX_VALUE && number.longValue() > Integer.MIN_VALUE) {
                return number.intValue();
            }
            return number;
        } catch (ParseException e) {
            return envVariable;
        }
    }

    private boolean isNumber(String envVariable) {
        return envVariable.matches("-?\\d+(\\.\\d+)?");
    }

}
