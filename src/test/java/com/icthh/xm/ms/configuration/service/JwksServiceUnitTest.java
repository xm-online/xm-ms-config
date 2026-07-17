package com.icthh.xm.ms.configuration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.domain.idp.model.IdpPublicConfig.IdpConfigContainer.IdpPublicClientConfig;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwksServiceUnitTest {

    @Mock
    private UpdateInMemoryEventService inMemoryService;
    @Mock
    private ConfigurationService configurationService;
    @Mock
    private JwkFetcher jwkFetcher;
    @InjectMocks
    private JwksService jwksService;

    private IdpPublicClientConfig client(String key, String jwksUri) {
        IdpPublicClientConfig config = mock(IdpPublicClientConfig.class, RETURNS_DEEP_STUBS);
        when(config.getKey()).thenReturn(key);
        when(config.getOpenIdConfig().getJwksEndpoint().getUri()).thenReturn(jwksUri);
        return config;
    }

    @Test
    void successfulFetchPublishesFreshKeys() {
        when(jwkFetcher.fetchJwk("https://certs")).thenReturn(Optional.of("{\"keys\":[\"fresh\"]}"));

        jwksService.updatePublicJwksConfiguration("TENANT", Set.of(),
            Map.of("Google", client("Google", "https://certs")));

        Map<String, Configuration> published = capturePublished();
        assertThat(published).containsOnlyKeys("/config/tenants/TENANT/config/idp/clients/Google-jwks-cache.json");
        assertThat(published.values().iterator().next().getContent()).isEqualTo("{\"keys\":[\"fresh\"]}");
    }

    @Test
    void failedFetchOfActiveClientKeepsExistingKeys_noEmptyDeletePublished() {
        // Google is an active client whose fetch fails -> must NOT be wiped.
        when(jwkFetcher.fetchJwk("https://certs")).thenReturn(Optional.empty());

        jwksService.updatePublicJwksConfiguration("TENANT", Set.of("Google"),
            Map.of("Google", client("Google", "https://certs")));

        // Nothing published => the existing in-memory keys for Google are left untouched.
        verifyNoInteractions(inMemoryService);
    }

    @Test
    void removedClientIsDeleted_activeClientWithFailedFetchIsKept() {
        // Facebook removed from config; Google still active but its fetch fails.
        when(jwkFetcher.fetchJwk("https://certs")).thenReturn(Optional.empty());

        jwksService.updatePublicJwksConfiguration("TENANT", Set.of("Google", "Facebook"),
            Map.of("Google", client("Google", "https://certs")));

        Map<String, Configuration> published = capturePublished();
        // Only the genuinely-removed client is emptied (deleted); Google is not touched.
        assertThat(published).containsOnlyKeys("/config/tenants/TENANT/config/idp/clients/Facebook-jwks-cache.json");
        assertThat(published.values().iterator().next().getContent()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Configuration> capturePublished() {
        ArgumentCaptor<Map<String, Configuration>> captor = ArgumentCaptor.forClass(Map.class);
        verify(inMemoryService).sendEvent(org.mockito.ArgumentMatchers.eq("TENANT"), captor.capture());
        return captor.getValue();
    }
}
