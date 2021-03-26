package com.icthh.xm.ms.configuration.repository.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.client.api.RefreshableConfiguration;
import com.icthh.xm.commons.domain.idp.IdpConfigUtils;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer.IdpPublicClientConfig;
import com.icthh.xm.ms.configuration.service.JwksService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final String KEY_TENANT = "tenant";
    private static final String IDP_EMPTY_CONFIG = "idp:";

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
    private final Map<String, Map<String, IdpPublicClientConfig>> tmpValidIdpClientPublicConfigs = new ConcurrentHashMap<>();

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

            List<IdpPublicClientConfig> rawIdpPublicClientConfigs =
                processPublicClientsConfiguration(tenantKey, configKey, config);

            boolean isRawClientsConfigurationEmpty = CollectionUtils.isEmpty(rawIdpPublicClientConfigs);
            boolean isValidClientsConfigurationEmpty = CollectionUtils.isEmpty(tmpValidIdpClientPublicConfigs.get(tenantKey));

            if (isRawClientsConfigurationEmpty && isValidClientsConfigurationEmpty) {
                log.warn("For tenant [{}] provided IDP public client configs not present." +
                    "Removing client configs from storage and jwks keys from file system", tenantKey);
                //remove all previously created jwks keys from storage
                jwksService.deletePublicJwksConfigurations(tenantKey, getIdpClientConfigsByTenantKey(tenantKey));
                idpClientConfigs.remove(tenantKey);
                return;
            }

            if (isValidClientsConfigurationEmpty) {
                log.info("For tenant [{}] provided IDP public client configs not applied.", tenantKey);
                return;
            }
            //remove all previously created jwks keys from storage
            jwksService.deletePublicJwksConfigurations(tenantKey, getIdpClientConfigsByTenantKey(tenantKey));
            updateInMemoryConfig(tenantKey);
            jwksService.createPublicJwksConfiguration(tenantKey, getIdpClientConfigsByTenantKey(tenantKey));

        } catch (Exception e) {
            log.error("Error occurred during processing idp config: {}, {}", e.getMessage(), e);
        }

    }

    private List<IdpPublicClientConfig> processPublicClientsConfiguration(String tenantKey, String configKey, String config) {
        if (!matcher.match(IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN, configKey)) {
            return Collections.emptyList();
        }

        List<IdpPublicClientConfig> rawIdpPublicClientConfigs =
            Optional.ofNullable(parseConfig(tenantKey, config, IdpPublicConfig.class))
            .map(IdpPublicConfig::getConfig)
            .map(IdpPublicConfig.IdpConfigContainer::getClients)
            .orElseGet(Collections::emptyList);

        rawIdpPublicClientConfigs
            .stream()
            .filter(IdpConfigUtils::isPublicClientConfigValid)
            .forEach(publicIdpConf -> setIdpPublicClientConfig(tenantKey, publicIdpConf));

        return rawIdpPublicClientConfigs;
    }

    @SneakyThrows
    private <T> T parseConfig(String tenantKey, String config, Class<T> configType) {
        T parsedConfig;
        try {
            parsedConfig = objectMapper.readValue(config, configType);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong during attempt to read config [{}] for tenant [{}]. " +
                "Creating default config.", config, tenantKey, e);
            parsedConfig = objectMapper.readValue(IDP_EMPTY_CONFIG, configType);
        }
        return parsedConfig;
    }

    private void setIdpPublicClientConfig(String tenantKey, IdpPublicClientConfig publicConfig) {
        tmpValidIdpClientPublicConfigs
            .computeIfAbsent(tenantKey, key -> new HashMap<>())
            .put(publicConfig.getKey(), publicConfig);
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
        idpClientConfigs.put(tenantKey, tmpValidIdpClientPublicConfigs.remove(tenantKey));
    }

    private String extractTenantKeyFromPath(String configKey) {
        return matcher
            .extractUriTemplateVariables(IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN, configKey)
            .get(KEY_TENANT);
    }

    public Map<String, IdpPublicClientConfig> getIdpClientConfigsByTenantKey(String tenantKey) {
        return idpClientConfigs.getOrDefault(tenantKey, new HashMap<>());
    }
}
