package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractUnitTest;
import com.icthh.xm.ms.configuration.config.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.util.Optional;
import org.mockito.Mockito;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SystemStubsExtension.class)
public class TokenKeyServiceUnitTest extends AbstractUnitTest {

    private TokenKeyService tokenKeyService;

    @Mock
    private ConfigurationService configurationService;

    @SystemStub
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @BeforeEach
    public void setUp() {
        tokenKeyService = new TokenKeyService(configurationService);

        Mockito.lenient().when(configurationService.findConfiguration(Constants.CONFIG + Constants.PUBLIC_KEY_FILE))
                .thenReturn(Optional.of(new Configuration(null, "config.cer")));
    }

    @Test
    public void shouldReturnPublicCerFromConfigRepo() {
        environmentVariables.set("PUBLIC_CER", null);

        String cer = tokenKeyService.getKey();

        assertThat(cer).isEqualTo("config.cer");
    }

    @Test
    public void shouldReturnPublicCerFromEnv() {
        environmentVariables.set("PUBLIC_CER", "env.cer");

        String cer = tokenKeyService.getKey();

        assertThat(cer).isEqualTo("env.cer");
    }
}
