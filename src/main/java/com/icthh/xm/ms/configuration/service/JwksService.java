package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer.IdpPublicClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.icthh.xm.commons.domain.idp.IdpConstants.JWKS_FILE_NAME_PATTERN;
import static com.icthh.xm.commons.domain.idp.IdpConstants.PUBLIC_JWKS_CONFIG_PATH_PATTERN;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwksService {
    public static final String TENANT_REPLACE_PATTERN = "{tenant}";
    public static final String IDP_CLIENT_KEY_REPLACE_PATTERN = "{idpClientKey}";

    private final ConfigurationService configurationService;

    public void createPublicJwksConfiguration(String tenantKey, Map<String, IdpPublicClientConfig> tenantClientConfigs) {
        Map<String, String> clientsJwks = getJwks(tenantClientConfigs);

        List<Configuration> configurations = clientsJwks.entrySet()
            .stream()
            .map(entry -> buildPublicJwksConfiguration(tenantKey, entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        configurationService.saveConfigurations(configurations, Map.of());
    }

    public void deletePublicJwksConfigurations(String tenantKey, Map<String, IdpPublicClientConfig> tenantClientConfigs) {
        //find all tenant jwks
        List<String> jwksPaths =
            tenantClientConfigs
                .keySet()
                .stream()
                .map(idpClientKey -> buildPathToJwks(tenantKey, idpClientKey))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(jwksPaths)) {
            return;
        }

        log.info("For tenant [{}] found following jwks paths: {}", tenantKey, jwksPaths);

        //delete all tenant jwks
        log.info("For tenant [{}] deleting following jwks: {}", tenantKey, jwksPaths);
        configurationService.deleteConfigurations(jwksPaths);
    }

    private Map<String, String> getJwks(Map<String, IdpPublicClientConfig> tenantClientConfigs) {
        return getJwkSetEndpoints(tenantClientConfigs).entrySet()
            .stream()
            .map(entry -> retrieveRawPublicKeysDefinition(entry.getKey(), entry.getValue()))
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, String> getJwkSetEndpoints(Map<String, IdpPublicClientConfig> idpClientConfigs) {
        return idpClientConfigs.entrySet()
            .stream()
            .map(entry -> Map.entry(entry.getKey(), entry.getValue().getOpenIdConfig().getJwksEndpoint().getUri()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SneakyThrows
    private Map.Entry<String, String> retrieveRawPublicKeysDefinition(String key, String jwksEndpointUri) {
        try {
            URL url = new URL(jwksEndpointUri);
            String content = IOUtils.toString(url.openStream(), StandardCharsets.UTF_8.name());
            if (StringUtils.isBlank(content)) {
                log.error("Received empty public key from url: {}", jwksEndpointUri);
                return null;
            }
            return Map.entry(key, content);
        } catch (MalformedURLException ex) {
            log.error("Invalid JWK Set URL: {}, {}", ex.getMessage(), ex);
        }
        return null;
    }

    private Configuration buildPublicJwksConfiguration(String tenantKey, String idpClientKey, String content) {
        return new Configuration(buildPathToJwks(tenantKey, idpClientKey), content);
    }

    private String buildPathToJwks(String tenantKey, String idpClientKey) {
        String path = PUBLIC_JWKS_CONFIG_PATH_PATTERN.replace(TENANT_REPLACE_PATTERN, tenantKey);
        String fileName = JWKS_FILE_NAME_PATTERN.replace(IDP_CLIENT_KEY_REPLACE_PATTERN, idpClientKey);

        return path + fileName;
    }
}
