package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class InconsistentConfigLogger {

    private final Yaml yaml;
    private final AtomicBoolean configUpdateInProgress = new AtomicBoolean(false);

    public InconsistentConfigLogger() {
        this.yaml = new Yaml();
    }

    void lock(String updateTriggerMethod) {
        configUpdateInProgress.set(true);
        log.info("Config update started by method={}", updateTriggerMethod);
    }

    void unlock() {
        configUpdateInProgress.set(false);
        log.info("Config update ended");
    }

    void logConfigGet(String triggerMethod) {
        if (configUpdateInProgress.get()) {
            log.warn("Config get during update, possible inconsistency triggerMethod={}", triggerMethod);
        }
    }

    @SneakyThrows
    void logAdditionalParameters(Map<String, Configuration> configs) {
        Optional<Map<String, Object>> tenantConfigYamlFile = Optional.ofNullable(configs.get("/config/tenants/XM/tenant-config.yml"))
            .map(Configuration::getContent)
            .map(yaml::load);
        String systemClientSecret = tenantConfigYamlFile
            .map(stringObjectMap -> (Map<String, Object>) stringObjectMap.get("uaa"))
            .map(stringObjectMap -> stringObjectMap.get("systemClientToken"))
            .map(Object::toString)
            .orElse(StringUtils.EMPTY);
        String redisHost = tenantConfigYamlFile
            .map(stringObjectMap -> (Map<String, Object>) stringObjectMap.get("commons"))
            .map(stringObjectMap -> (Map<String, Object>) stringObjectMap.get("redis"))
            .map(stringObjectMap -> stringObjectMap.get("host"))
            .map(Object::toString)
            .orElse(StringUtils.EMPTY);
        log.info("getPrivateConfigs: finalConfigs systemClientSecret={} redisHost={}", systemClientSecret, redisHost);
    }
}
