package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.annotations.VisibleForTesting;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.utils.ConfigPathUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * Service that configures roles and permissions based on UAA microservice values.
 * After the application is started, polls UAA configuration endpoint to
 * get the configuration and then updates in-memory values. This flow is tenant-specific
 * and turns ons via {@link #UAA_PERMISSIONS_PROPERTY} property in {@code /tenant-config.yml}.
 * This allows keeping the configuration outside of VCS.
 *
 * <p>NOTE: It's expected that roles.yml and permissions.yml are added to .gitignore for
 * the configured tenants
 */
@Component
@Slf4j
class PermissionConfigurationService {

    public static final String UAA_PERMISSIONS_PROPERTY = "uaa-permissions";
    public static final String TENANT_CONFIG_YML = "/tenant-config.yml";
    private final RestTemplate restTemplate;
    private final ConfigurationService configService;
    private final TenantService tenantService;

    @Value("${jhipster.security.client-authorization.access-token-uri:http://uaa/oauth/token}")
    private String tokenUrl = "http://uaa/oauth/token";
    @Value("${jhipster.security.client-authorization.client-id:internal}")
    private String clientId;
    @Value("${jhipster.security.client-authorization.client-secret:internal}")
    private String clientSecret;
    @Value("${application.uaa-permissions.url:http://uaa}")
    private String uaaUrl = "http://uaa";

    public PermissionConfigurationService(@Lazy RestTemplate loadBalancedRestTemplate, ConfigurationService configService, TenantService tenantService) {
        this.restTemplate = loadBalancedRestTemplate;
        this.configService = configService;
        this.tenantService = tenantService;
    }

    @EventListener
    @Retryable(maxAttempts = Integer.MAX_VALUE,
        backoff = @Backoff(delayExpression = "${application.uaa-permissions.retry-delay}"))
    public void started(ApplicationReadyEvent event) {
        log.info("Attempting to get permission configuration from UAA");

        updateConfigurationFromUaa();
    }

    @VisibleForTesting
    void updateConfigurationFromUaa() {
        tenantService.getTenants("uaa").stream()
            .filter(t -> "ACTIVE".equals(t.getState()))
            .map(t -> t.getName().toUpperCase())
            .filter(this::isUaaPermissionsEnabled)
            .forEach(this::updatePermissionConfiguration);
    }

    private void updatePermissionConfiguration(String tenantKey) {
        String token = getAuthToken(tenantKey);

        configService.updateConfigurationInMemory(Configuration.of()
            .path(String.format("/config/tenants/%s/roles.yml", tenantKey))
            .content(getConfig(token, String.format(uaaUrl + "/roles/%s/configuration", tenantKey)))
            .build());

        configService.updateConfigurationInMemory(Configuration.of()
            .path(String.format("/config/tenants/%s/permissions.yml", tenantKey))
            .content(getConfig(token, String.format(uaaUrl + "/permissions/%s/configuration", tenantKey)))
            .build());
    }

    @SneakyThrows
    private boolean isUaaPermissionsEnabled(String tenantKey) {
        Collection<Configuration> values = configService.getConfigurationMap(null, Collections.singleton(
            ConfigPathUtils.getTenantPathPrefix(tenantKey) + TENANT_CONFIG_YML)).values();

        Configuration configuration;
        if (CollectionUtils.isEmpty(values) || (configuration = values.iterator().next()) == null) {
            return false;
        }

        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        Map<String, Object> value = objectMapper.readValue(configuration.getContent(), Map.class);

        return Optional.ofNullable((Boolean) value.get(UAA_PERMISSIONS_PROPERTY))
            .orElse(false);
    }

    private String getConfig(String token, String url) {
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.add("Authorization", token);

        return restTemplate.exchange(url,
            HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }

    private String getAuthToken(String tenantName) {
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.add("x-tenant", tenantName);
        headers.add("Authorization", "Basic " + new String(Base64.encodeBase64(String.format("%s:%s", clientId, clientSecret).getBytes())));

        Map<String, String> response = restTemplate.exchange(tokenUrl + "?grant_type=client_credentials",
            HttpMethod.POST, new HttpEntity<>(headers), Map.class).getBody();

        return String.format("%s %s", response.get("token_type"), response.get(("access_token")));
    }

}
