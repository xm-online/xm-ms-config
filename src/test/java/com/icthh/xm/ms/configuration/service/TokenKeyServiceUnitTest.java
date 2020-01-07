package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.Constants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TokenKeyServiceUnitTest {

    private TokenKeyService tokenKeyService;

    @Mock
    private ConfigurationService configurationService;

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void setUp() {
        tokenKeyService = new TokenKeyService(configurationService);

        when(configurationService.findConfiguration(Constants.CONFIG + Constants.PUBLIC_KEY_FILE))
                .thenReturn(Optional.of(new Configuration(null, "config.cer")));
    }

    @Test
    public void shouldReturnPublicCerFromConfigRepo() {
        environmentVariables.set("PUBLIC_CER", null);

        String cer = tokenKeyService.getKey();

        assertThat(cer).isEqualTo( "config.cer");
    }

    @Test
    public void shouldReturnPublicCerFromEnv() {
        environmentVariables.set("PUBLIC_CER", "env.cer");

        String cer = tokenKeyService.getKey();

        assertThat(cer).isEqualTo( "env.cer");
    }
}
