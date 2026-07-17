package com.icthh.xm.ms.configuration.config;

import com.icthh.xm.commons.config.client.api.FetchConfigurationSettings;
import com.icthh.xm.commons.domain.idp.IdpConstants;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Overrides the default {@link FetchConfigurationSettings} for the config service so that tenant IDP public
 * configs ({@code .../webapp/public/idp-config-public.yml}) are included in the set of configs delivered to
 * {@link com.icthh.xm.commons.config.client.api.RefreshableConfiguration} beans.
 *
 * <p>The refreshable config map is built via {@code getConfigMapAntPattern(null, msConfigPatterns)}. The default
 * {@code msConfigPatterns} does not match {@code .../webapp/public/idp-config-public.yml}, so
 * {@link com.icthh.xm.ms.configuration.repository.impl.IdpConfigRepository} never receives idp configs and the
 * JWKS fetch/refresh pipeline never starts — leaving UAA without Google public keys
 * ({@code Invalid JOSE Header kid}). Adding the idp public config path fixes the trigger for both the initial
 * load and subsequent refreshes.
 */
@Configuration
public class IdpFetchConfigurationSettingsConfiguration {

    @Bean
    @Primary
    public FetchConfigurationSettings idpAwareFetchConfigurationSettings(
            @Value("${spring.application.name}") String applicationName,
            @Value("${application.config-fetch-all.enabled:false}") Boolean isFetchAll) {
        return new FetchConfigurationSettings(applicationName, isFetchAll) {
            private final List<String> patterns = withIdpPublicConfig(super.getMsConfigPatterns());

            @Override
            public List<String> getMsConfigPatterns() {
                return patterns;
            }
        };
    }

    private static List<String> withIdpPublicConfig(List<String> basePatterns) {
        List<String> patterns = new ArrayList<>(basePatterns);
        patterns.add(IdpConstants.IDP_PUBLIC_SETTINGS_CONFIG_PATH_PATTERN);
        return List.copyOf(patterns);
    }
}
