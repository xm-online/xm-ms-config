package com.icthh.xm.ms.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.SingleResultAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * The old private API /api/private/config_map without authorization works only with the profile migration.
 */
@Configuration
public class OldPrivateApiAccessConfiguration {

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
}
