package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer.IdpPublicClientConfig;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

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
@Component
@RequiredArgsConstructor
public class JwksService {
    public static final String TENANT_REPLACE_PATTERN = "{tenant}";
    public static final String IDP_CLIENT_KEY_REPLACE_PATTENR = "{idpClientKey}";

    private final DistributedConfigRepository repositoryProxy;

    public void createPublicJwksConfiguration(String tenantKey, Map<String, IdpPublicClientConfig> tenantClientConfigs) {
        Map<String, String> clientsJwks = getJwks(tenantClientConfigs);

        List<Configuration> configurations = clientsJwks.entrySet()
            .stream()
            .map(entry -> buildPublicJwksConfiguration(tenantKey, entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        repositoryProxy.saveAll(configurations);
    }

    private Map<String, String> getJwks(Map<String, IdpPublicClientConfig> tenantClientConfigs) {

        return getJwkSetEndpoints(tenantClientConfigs).entrySet()
            .stream()
            .map(entry -> {
                String keysDefinition = retrieveRawPublicKeysDefinition(entry.getValue());
                if (StringUtils.isEmpty(keysDefinition)) {
                    return null;
                }
                return Map.entry(entry.getKey(), keysDefinition);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, String> getJwkSetEndpoints(Map<String, IdpPublicClientConfig> idpClientConfigs) {

        return idpClientConfigs.entrySet()
            .stream()
            .map(entry -> {
                IdpPublicClientConfig publicClientConfig = entry.getValue();
                String uri = publicClientConfig.getOpenIdConfig().getJwksEndpoint().getUri();
                return Map.entry(entry.getKey(), uri);
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SneakyThrows
    public String retrieveRawPublicKeysDefinition(String jwksEndpointUri) {
        try {
            URL url = new URL(jwksEndpointUri);
            String content = IOUtils.toString(url.openStream(), StandardCharsets.UTF_8.name());
            if (StringUtils.isBlank(content)) {
                log.error("Received empty public key from url: {}", jwksEndpointUri);
            }
            return content;
        } catch (MalformedURLException ex) {
            log.error("Invalid JWK Set URL: {}, {}", ex.getMessage(), ex);
        }
        return null;
    }

    private Configuration buildPublicJwksConfiguration(String tenantKey, String idpClientKey, String content) {
        String path = PUBLIC_JWKS_CONFIG_PATH_PATTERN.replace(TENANT_REPLACE_PATTERN, tenantKey);
        String fileName = JWKS_FILE_NAME_PATTERN.replace(IDP_CLIENT_KEY_REPLACE_PATTENR, idpClientKey);

        return new Configuration(path + fileName, content);
    }
}
