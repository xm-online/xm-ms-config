package com.icthh.xm.ms.configuration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.client.api.ConfigurationChangedListener;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

@RunWith(MockitoJUnitRunner.class)
public class LocalConfigServiceUnitTest {

    @InjectMocks
    private ConfigurationService configService;

    @Mock
    private DistributedConfigRepository inMemoryRepository;
    @Mock
    private ConfigurationChangedListener configurationListener;
    @Spy
    private ConfigVersionDeserializer configVersionDeserializer = new ConfigVersionDeserializer();

    @Test
    public void getConfigurationMap() {
        Map<String, Configuration> config = Collections.singletonMap("path", new Configuration("path", "content"));
        when(inMemoryRepository.getMap(new ConfigVersion("commit")))
            .thenReturn(config);

        assertThat(configService.getConfigurationMap("commit")).isEqualTo(config);
    }

    @Test
    public void updateConfigurations() {
        Map<String, Configuration> config = Collections.singletonMap("path", new Configuration("path", "content"));
        when(inMemoryRepository.getMap(new ConfigVersion("commit"))).thenReturn(config);

        configService.addConfigurationChangedListener(configurationListener);
        configService.updateConfigurations("commit", Collections.singletonList("path"));

        verify(configurationListener).onConfigurationChanged(config.get("path"));
    }
}
