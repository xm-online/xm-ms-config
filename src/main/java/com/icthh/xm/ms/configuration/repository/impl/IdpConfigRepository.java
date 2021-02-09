package com.icthh.xm.ms.configuration.repository.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.commons.domain.idp.IdpConfigUtils;
import com.icthh.xm.commons.domain.idp.IdpPublicConfig;
import com.icthh.xm.commons.domain.idp.IdpPublicConfig.IdpConfigContainer.IdpPublicClientConfig;
import com.icthh.xm.ms.configuration.service.JwksService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.icthh.xm.commons.domain.idp.IdpConstants.IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN;

/**
 * This class reads and process IDP clients public configuration for each tenant.
 * Tenant IDP clients created for each successfully loaded config. If config not loaded or invalid it skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdpConfigRepository implements RefreshableConfiguration {

    public static final String KEY_TENANT = "tenant";

    private final JwksService jwksService;

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * In memory storage for storing information tenant IDP clients public.
     */
    private final Map<String, Map<String, IdpPublicClientConfig>> idpClientConfigs = new ConcurrentHashMap<>();

    /**
     * In memory storage.
     * Stores information about tenant IDP clients public configuration that currently in process.
     * <p/>
     * We need to store this information in memory to avoid corruption previously registered in-memory tenant clients config
     */
    private final Map<String, Map<String, IdpPublicClientConfig>> tmpIdpClientPublicConfigs = new ConcurrentHashMap<>();

    @Override
    public void onRefresh(String updatedKey, String config) {
        updateIdpConfigs(updatedKey, config);
    }

    @Override
    public boolean isListeningConfiguration(String updatedKey) {
        return matcher.match(IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN, updatedKey);
    }

    @Override
    public void onInit(String configKey, String configValue) {
        updateIdpConfigs(configKey, configValue);
    }

    private void updateIdpConfigs(String configKey, String config) {
        String tenantKey = extractTenantKeyFromPath(configKey);

        processPublicConfiguration(tenantKey, configKey, config);

        boolean isClientConfigurationEmpty = CollectionUtils.isEmpty(tmpIdpClientPublicConfigs.get(tenantKey));

        if (isClientConfigurationEmpty) {
            log.info("For tenant [{}] provided IDP public client configs not applied.", tenantKey);
            return;
        }

        updateInMemoryConfig(tenantKey);
        jwksService.createPublicJwksConfiguration(tenantKey, getIdpClientConfigsByTenantKey(tenantKey));
    }

    private void processPublicConfiguration(String tenantKey, String configKey, String config) {
        if (!matcher.match(IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN, configKey)) {
            return;
        }
        IdpPublicConfig idpPublicConfig = parseConfig(tenantKey, config, IdpPublicConfig.class);

        if (idpPublicConfig != null && idpPublicConfig.getConfig() != null) {
            idpPublicConfig
                .getConfig()
                .getClients()
                .forEach(publicIdpConf -> {
                        if (IdpConfigUtils.isPublicConfigValid(tenantKey, publicIdpConf)) {
                            String idpConfKey = publicIdpConf.getKey();

                            Map<String, IdpPublicClientConfig> idpPublicConfigs =
                                tmpIdpClientPublicConfigs.computeIfAbsent(tenantKey, key -> new HashMap<>());
                            idpPublicConfigs.put(idpConfKey, publicIdpConf);
                        }
                    }
                );
        }
    }

    @SneakyThrows
    private <T> T parseConfig(String tenantKey, String config, Class<T> configType) {
        T parsedConfig = null;
        try {
            parsedConfig = objectMapper.readValue(config, configType);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong during attempt to read {} for tenant:{}", config.getClass(), tenantKey, e);
        }
        return parsedConfig;
    }

    /**
     * <p>
     * Basing on input configuration method removes all previously registered clients for specified tenant
     * to avoid redundant clients registration presence
     * </p>
     *
     * @param tenantKey tenant key
     */
    private void updateInMemoryConfig(String tenantKey) {
        idpClientConfigs.put(tenantKey, tmpIdpClientPublicConfigs.get(tenantKey));
        tmpIdpClientPublicConfigs.remove(tenantKey);
    }

    private String extractTenantKeyFromPath(String configKey) {
        Map<String, String> configKeyParams =
            matcher.extractUriTemplateVariables(IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN, configKey);

        return configKeyParams.get(KEY_TENANT);
    }

    public Map<String, IdpPublicClientConfig> getIdpClientConfigsByTenantKey(String tenantKey) {
        return idpClientConfigs.getOrDefault(tenantKey, new HashMap<>());
    }
}