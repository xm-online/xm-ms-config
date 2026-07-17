package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.commons.domain.idp.IdpConstants.JWKS_FILE_NAME_PATTERN;
import static com.icthh.xm.commons.domain.idp.IdpConstants.PUBLIC_JWKS_CONFIG_PATH_PATTERN;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer.IdpPublicClientConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    private final UpdateInMemoryEventService inMemoryService;
    private final ConfigurationService configurationService;
    private final JwkFetcher jwkFetcher;

    public void updatePublicJwksConfiguration(String tenantKey,
                                              Set<String> oldClients,
                                              Map<String, IdpPublicClientConfig> newClients) {
        Map<String, Configuration> jwks = new HashMap<>();
        jwks.putAll(removedClientsJwks(tenantKey, oldClients, newClients));
        jwks.putAll(fetchedClientsJwks(tenantKey, newClients));

        if (jwks.isEmpty()) {
            log.warn("No JWKS changes to publish for tenant [{}]", tenantKey);
            return;
        }
        inMemoryService.sendEvent(tenantKey, jwks);
    }

    /**
     * Clients removed from the public config: empty content deletes their cached JWKS.
     */
    private Map<String, Configuration> removedClientsJwks(String tenantKey,
                                                          Set<String> oldClients,
                                                          Map<String, IdpPublicClientConfig> newClients) {
        return oldClients.stream()
            .filter(clientKey -> !newClients.containsKey(clientKey))
            .map(clientKey -> jwkConfiguration(tenantKey, clientKey, ""))
            .collect(toMap(Configuration::getPath, identity()));
    }

    /**
     * Current clients: fetch fresh keys, skipping any client whose fetch failed so its existing keys are kept.
     */
    private Map<String, Configuration> fetchedClientsJwks(String tenantKey,
                                                          Map<String, IdpPublicClientConfig> newClients) {
        return newClients.values().stream()
            .map(clientConfig -> fetchJwkConfiguration(tenantKey, clientConfig))
            .flatMap(Optional::stream)
            .collect(toMap(Configuration::getPath, identity()));
    }

    /**
     * Fetches JWKS for a single client, or empty when the fetch failed — never publishes empty content,
     * which would delete the client's keys and break login until the next successful refresh.
     */
    private Optional<Configuration> fetchJwkConfiguration(String tenantKey, IdpPublicClientConfig clientConfig) {
        String jwkSetEndpoint = clientConfig.getOpenIdConfig().getJwksEndpoint().getUri();
        Optional<String> jwk = jwkFetcher.fetchJwk(jwkSetEndpoint);
        if (jwk.isEmpty()) {
            log.warn("Skip JWK update for tenant [{}] client [{}]: fetch failed, keeping existing keys",
                tenantKey, clientConfig.getKey());
        }
        return jwk.map(content -> jwkConfiguration(tenantKey, clientConfig.getKey(), content));
    }

    private Configuration jwkConfiguration(String tenantKey, String idpClientKey, String content) {
        return new Configuration(buildPathToJwks(tenantKey, idpClientKey), content);
    }

    private String buildPathToJwks(String tenantKey, String idpClientKey) {
        String path = PUBLIC_JWKS_CONFIG_PATH_PATTERN.replace(TENANT_REPLACE_PATTERN, tenantKey);
        String fileName = JWKS_FILE_NAME_PATTERN.replace(IDP_CLIENT_KEY_REPLACE_PATTERN, idpClientKey);
        return path + fileName;
    }

}
