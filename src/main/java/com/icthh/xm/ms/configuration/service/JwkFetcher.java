package com.icthh.xm.ms.configuration.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwkFetcher {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    /**
     * Fetches JWKS content from the given IdP endpoint.
     *
     * <p>Returns {@link Optional#empty()} on any failure (network error, timeout or empty body) so callers
     * keep the previously published keys instead of overwriting them with empty content — empty content is
     * treated as a delete by the in-memory config storage, which would wipe a tenant's keys on a transient
     * failure and break IdP login until the next successful refresh.
     */
    public Optional<String> fetchJwk(String jwksEndpointUri) {
        try {
            log.info("Fetch jwk from URL {}", jwksEndpointUri);
            URLConnection connection = URI.create(jwksEndpointUri).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            try (InputStream inputStream = connection.getInputStream()) {
                String jwk = IOUtils.toString(inputStream, UTF_8);
                if (StringUtils.isBlank(jwk)) {
                    log.error("Fetched empty jwk from URL {}", jwksEndpointUri);
                    return Optional.empty();
                }
                return Optional.of(jwk);
            }
        } catch (Exception ex) {
            log.error("Error fetch jwk from URL {}", jwksEndpointUri, ex);
            return Optional.empty();
        }
    }

}
