package com.icthh.xm.ms.configuration.security;


import static com.icthh.xm.ms.configuration.config.Constants.*;

import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Overrides to get token tenant.
 */
public class DomainJwtAccessTokenConverter extends JwtAccessTokenConverter {

    @Override
    public OAuth2Authentication extractAuthentication(Map<String, ?> map) {
        final OAuth2Authentication authentication = super.extractAuthentication(map);
        final Map<String, String> details = new HashMap<>();
        details.put(AUTH_TENANT_KEY, (String) map.get(AUTH_TENANT_KEY));
        details.put(AUTH_XM_TOKEN_KEY, (String) map.get(AUTH_XM_TOKEN_KEY));
        details.put(AUTH_XM_COOKIE_KEY, (String) map.get(AUTH_XM_COOKIE_KEY));
        details.put(AUTH_XM_USERID_KEY, (String) map.get(AUTH_XM_USERID_KEY));
        details.put(AUTH_XM_LOCALE_KEY, (String) map.get(AUTH_XM_LOCALE_KEY));
        authentication.setDetails(details);

        return authentication;
    }
}
