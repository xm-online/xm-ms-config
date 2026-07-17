package com.icthh.xm.ms.configuration.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.icthh.xm.commons.config.client.api.FetchConfigurationSettings;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/**
 * Proves the root cause and the fix: the default {@link FetchConfigurationSettings} patterns do NOT cover the
 * IDP public config path, so the config service never delivers it to {@code IdpConfigRepository}. The override
 * adds a matching pattern.
 */
class IdpFetchConfigurationSettingsUnitTest {

    private static final String IDP_PUBLIC_CONFIG_PATH =
        "/config/tenants/SSP/webapp/public/idp-config-public.yml";

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Test
    void defaultPatternsDoNotCoverIdpPublicConfig_reproducesBug() {
        FetchConfigurationSettings defaults = new FetchConfigurationSettings("config", false);

        boolean covered = defaults.getMsConfigPatterns().stream()
            .anyMatch(pattern -> matcher.match(pattern, IDP_PUBLIC_CONFIG_PATH));

        assertThat(covered)
            .as("default msConfigPatterns must not cover the idp public config (this is the bug)")
            .isFalse();
    }

    @Test
    void overriddenPatternsCoverIdpPublicConfig_verifiesFix() {
        FetchConfigurationSettings fixed = new IdpFetchConfigurationSettingsConfiguration()
            .idpAwareFetchConfigurationSettings("config", false);

        boolean covered = fixed.getMsConfigPatterns().stream()
            .anyMatch(pattern -> matcher.match(pattern, IDP_PUBLIC_CONFIG_PATH));

        assertThat(covered)
            .as("overridden msConfigPatterns must cover the idp public config so IdpConfigRepository is fed")
            .isTrue();
    }
}
