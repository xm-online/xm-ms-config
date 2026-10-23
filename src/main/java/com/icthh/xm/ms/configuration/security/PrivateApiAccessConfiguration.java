package com.icthh.xm.ms.configuration.security;

import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.SingleResultAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Access to the private API. The old /api/private/config_map without authorization works only with the profile
 * migration; /api/private/v2/config_map needs application.config-public-key, without the profile migration
 * xm-ms-config does not start without it.
 */
@Configuration
public class PrivateApiAccessConfiguration {

    public static final String OLD_PRIVATE_API_ACCESS = "oldPrivateApiAccess";

    private static final String MIGRATION_PROFILE = "migration";

    @Bean(OLD_PRIVATE_API_ACCESS)
    @Profile(MIGRATION_PROFILE)
    public AuthorizationManager<RequestAuthorizationContext> oldPrivateApiPermitted() {
        return SingleResultAuthorizationManager.permitAll();
    }

    @Bean(OLD_PRIVATE_API_ACCESS)
    @Profile("!" + MIGRATION_PROFILE)
    public AuthorizationManager<RequestAuthorizationContext> oldPrivateApiDenied() {
        return SingleResultAuthorizationManager.denyAll();
    }

    @Bean
    @Profile(MIGRATION_PROFILE)
    public ConfigClientTokenAuthorizationManager migrationConfigClientToken(ApplicationProperties properties) {
        return new ConfigClientTokenAuthorizationManager(true, properties.getConfigPublicKey());
    }

    @Bean
    @Profile("!" + MIGRATION_PROFILE)
    public ConfigClientTokenAuthorizationManager configClientToken(ApplicationProperties properties) {
        return new ConfigClientTokenAuthorizationManager(false, properties.getConfigPublicKey());
    }
}
