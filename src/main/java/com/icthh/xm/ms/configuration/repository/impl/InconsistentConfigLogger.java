package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@Component
public class InconsistentConfigLogger {

    private final Yaml yaml;
    private final AtomicBoolean configUpdateInProgress = new AtomicBoolean(false);
    private final Map<String, StackTraceElement[]> lockedByThreads = new ConcurrentHashMap<>();

    public InconsistentConfigLogger() {
        this.yaml = new Yaml();
    }

    void lock(String updateTriggerMethod) {
        configUpdateInProgress.set(true);

        String traceToLog = Optional.ofNullable(MDC.get("rid"))
            .orElse(Thread.currentThread().getName());

        lockedByThreads.put(traceToLog, Thread.currentThread().getStackTrace());
        log.info("Config update started by method={}", updateTriggerMethod);
    }

    void unlock() {
        String traceToLog = Optional.ofNullable(MDC.get("rid"))
            .orElse(Thread.currentThread().getName());

        configUpdateInProgress.set(false);
        lockedByThreads.remove(traceToLog);
        log.info("Config update ended");
    }

    void logConfigGet(String triggerMethod) {
        if (configUpdateInProgress.get()) {
            String message = "___threadName=%s threadStacksTrace=%s;";
            String threadsStacksTraceMessage = lockedByThreads.entrySet()
                .stream()
                .map(stringStringEntry -> String.format(message, stringStringEntry.getKey(),
                        Arrays.stream(stringStringEntry.getValue())
                            .limit(20)
                            .map(StackTraceElement::toString)
                            .collect(Collectors.joining(System.lineSeparator()))
                    )
                )
                .collect(Collectors.joining(System.lineSeparator()));

            log.warn(
                "Config get during update, possible inconsistency triggerMethod={}, lockedByNumberOfThreads={} {}",
                triggerMethod,
                lockedByThreads.size(),
                threadsStacksTraceMessage
            );
        }
    }

    @SneakyThrows
    void logAdditionalParameters(String name, Map<String, Configuration> configs) {
        Optional<Map<String, Object>> tenantConfigYamlFile = Optional.ofNullable(
                configs.get("/config/tenants/XM/tenant-config.yml"))
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

        log.info("logAdditionalParameters: name: {} systemClientSecret={} redisHost={} configs.size={}", name,
            systemClientSecret, redisHost, configs.size());
    }

    @SneakyThrows
    void logPrivateKeys(Map<String, Configuration> configs) {
        if (configUpdateInProgress.get()) {
            log.info("logAdditionalParameters: privateConfigKeys={}", configs.keySet());
        }
    }
}
