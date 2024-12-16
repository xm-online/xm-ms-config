package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.commons.domain.idp.IdpConstants.JWKS_FILE_NAME_PATTERN;
import static com.icthh.xm.commons.domain.idp.IdpConstants.PUBLIC_JWKS_CONFIG_PATH_PATTERN;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer.IdpPublicClientConfig;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwksService {
    public static final String TENANT_REPLACE_PATTERN = "{tenant}";
    public static final String IDP_CLIENT_KEY_REPLACE_PATTERN = "{idpClientKey}";

    private final ConfigurationService configurationService;
    private final JwkFetcher jwkFetcher;

    public void updatePublicJwksConfiguration(String tenantKey,
                                              Set<String> oldClients,
                                              Map<String, IdpPublicClientConfig> newClients) {
        Map<String, Configuration> jwks = oldClients.stream()
            .map(idpClientKey -> builderJwkConfiguration(tenantKey, idpClientKey, ""))
            .collect(toMap(Configuration::getPath, identity()));
        jwks.putAll(getJwks(tenantKey, newClients));
        configurationService.saveConfigurations(new ArrayList<>(jwks.values()), Map.of());
    }

    private Configuration builderJwkConfiguration(String tenantKey, String idpClientKey, String content) {
        return new Configuration(buildPathToJwks(tenantKey, idpClientKey), content);
    }

    private Map<String, Configuration> getJwks(String tenantKey, Map<String, IdpPublicClientConfig> tenantClientConfigs) {
        return tenantClientConfigs.values().stream()
            .map(it -> toJwkConfiguration(tenantKey, it))
            .collect(toMap(Configuration::getPath, identity()));
    }

    private Configuration toJwkConfiguration(String tenantKey, IdpPublicClientConfig idpPublicClientConfig) {
        String jwkSetEndpoint = idpPublicClientConfig.getOpenIdConfig().getJwksEndpoint().getUri();
        String jwk = jwkFetcher.fetchJwk(jwkSetEndpoint);
        String idpClientKey = idpPublicClientConfig.getKey();
        return builderJwkConfiguration(tenantKey, idpClientKey, jwk);
    }

    private String buildPathToJwks(String tenantKey, String idpClientKey) {
        String path = PUBLIC_JWKS_CONFIG_PATH_PATTERN.replace(TENANT_REPLACE_PATTERN, tenantKey);
        String fileName = JWKS_FILE_NAME_PATTERN.replace(IDP_CLIENT_KEY_REPLACE_PATTERN, idpClientKey);
        return path + fileName;
    }

}
