package com.icthh.xm.ms.configuration.repository.impl;

import static com.icthh.xm.commons.domain.idp.IdpConstants.IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN;
import static org.springframework.util.CollectionUtils.isEmpty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.commons.domain.idp.IdpConfigUtils;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer.Features;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer.IdpPublicClientConfig;
import com.icthh.xm.ms.configuration.service.UpdateJwkEventService;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * This class reads and process IDP clients public configuration for each tenant.
 * Tenant IDP clients created for each successfully loaded config. If config not loaded or invalid it skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdpConfigRepository implements RefreshableConfiguration {

    private static final String KEY_TENANT = "tenant";

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * In memory storage for storing information tenant IDP clients public.
     */
    private final Map<String, Map<String, IdpPublicClientConfig>> idpClientConfigs = new ConcurrentHashMap<>();

    private final UpdateJwkEventService jwksService;
    private final ThreadPoolTaskScheduler scheduler = createScheduler();
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final UpdateJwkEventService updateJwkEventService;

    private static ThreadPoolTaskScheduler createScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public boolean isListeningConfiguration(String updatedKey) {
        return matcher.match(IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN, updatedKey);
    }

    @Override
    public void onInit(String configKey, String configValue) {
        updateIdpConfigs(configKey, configValue);
    }

    @Override
    public void onRefresh(String updatedKey, String config) {
        updateIdpConfigs(updatedKey, config);
    }

    private void updateIdpConfigs(String configKey, String config) {
        try {
            String tenantKey = extractTenantKeyFromPath(configKey);

            IdpPublicConfig idpPublicConfig = parseConfig(tenantKey, config);

            Map<String, IdpPublicClientConfig> oldClients = getIdpClientConfigsByTenantKey(tenantKey);
            Map<String, IdpPublicClientConfig> newConfigs = processPublicClientsConfiguration(configKey, idpPublicConfig);
            scheduleUpdateByTtl(configKey, newConfigs, idpPublicConfig, tenantKey);

            if (isEmpty(newConfigs)) {
                log.warn("For tenant {} provided IDP public client configs not present." +
                    "Removing client configs from storage and jwks keys from file system", tenantKey);
                idpClientConfigs.remove(tenantKey);
            } else {
                log.info("Update IDP public clients for tenant {} to {}", tenantKey, newConfigs.keySet());
                idpClientConfigs.put(tenantKey, newConfigs);
            }

            jwksService.sendEvent(tenantKey, oldClients.keySet(), newConfigs);
        } catch (Exception e) {
            log.error("Error occurred during processing idp config: {}", e.getMessage(), e);
        }
    }

    private void scheduleUpdateByTtl(String configKey, Map<String, IdpPublicClientConfig> config, IdpPublicConfig idpPublicConfig, String tenantKey) {
        var existsJob = tasks.remove(tenantKey);
        if (existsJob != null) {
            log.info("Cancel scheduled update for tenant [{}]", tenantKey);
            existsJob.cancel(false);
        }

        int jwkTtl = getJwkTtl(idpPublicConfig);
        if (jwkTtl < 0 || isEmpty(config)) {
            log.warn("JWK TTL is not set for tenant [{}]. JWK update will not be scheduled", tenantKey);
            return;
        }

        var future = scheduler.schedule(() -> {
            log.info("Start scheduled update for tenant [{}]", tenantKey);
            updateJwkEventService.emitUpdateEvent(configKey);
        }, Instant.now().plusSeconds(jwkTtl));
        tasks.put(tenantKey, future);
        log.info("Scheduled update for tenant [{}] in [{}] seconds", tenantKey, jwkTtl);
    }

    private Integer getJwkTtl(IdpPublicConfig idpPublicConfig) {
        return Optional.ofNullable(idpPublicConfig)
            .map(IdpPublicConfig::getConfig)
            .map(IdpConfigContainer::getFeatures)
            .map(Features::getJwkTtl)
            .orElse(-1);
    }

    private Map<String, IdpPublicClientConfig> processPublicClientsConfiguration(String configKey, IdpPublicConfig config) {
        if (!matcher.match(IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN, configKey)) {
            return Collections.emptyMap();
        }

        List<IdpPublicClientConfig> rawIdpPublicClientConfigs =
            Optional.ofNullable(config)
            .map(IdpPublicConfig::getConfig)
            .map(IdpConfigContainer::getClients)
            .orElseGet(Collections::emptyList);

        Map<String, IdpPublicClientConfig> idpClientPublicConfigs = new HashMap<>();
        rawIdpPublicClientConfigs
            .stream()
            .filter(IdpConfigUtils::isPublicClientConfigValid)
            .forEach(publicIdpConf -> idpClientPublicConfigs.put(publicIdpConf.getKey(), publicIdpConf));

        return idpClientPublicConfigs;
    }

    @SneakyThrows
    private IdpPublicConfig parseConfig(String tenantKey, String config) {
        try {
            return objectMapper.readValue(config, IdpPublicConfig.class);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong during attempt to read config [{}] for tenant [{}]. " +
                "Creating default config.", config, tenantKey, e);
            return new IdpPublicConfig();
        }
    }

    private String extractTenantKeyFromPath(String configKey) {
        return matcher
            .extractUriTemplateVariables(IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN, configKey)
            .get(KEY_TENANT);
    }

    private Map<String, IdpPublicClientConfig> getIdpClientConfigsByTenantKey(String tenantKey) {
        return idpClientConfigs.getOrDefault(tenantKey, Map.of());
    }

}
