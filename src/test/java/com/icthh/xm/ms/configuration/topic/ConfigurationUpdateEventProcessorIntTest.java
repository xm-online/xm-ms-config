package com.icthh.xm.ms.configuration.topic;

import com.icthh.xm.commons.config.client.repository.message.ConfigurationUpdateMessage;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.lep.api.LepManagementService;
import com.icthh.xm.commons.tenant.internal.DefaultTenantContextHolder;
import com.icthh.xm.ms.configuration.service.ConcurrentConfigModificationException;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

public class ConfigurationUpdateEventProcessorIntTest {

    private static final String TENANT = "TEST";
    private static final String PATH = "/config/tenants/TEST/service/file";
    private static final String CONTENT = "content";
    private static final String OLD_CONFIG_HASH = "hash";

    private static final ConfigurationUpdateMessage MESSAGE = new ConfigurationUpdateMessage(PATH, CONTENT, OLD_CONFIG_HASH);

    @Mock
    private LepManagementService lepManagementService;

    @Mock
    private ConfigurationService configurationService;

    private ConfigurationUpdateEventProcessor processor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        processor = new ConfigurationUpdateEventProcessor(new DefaultTenantContextHolder(), lepManagementService, configurationService);
    }

    @Test
    public void process() {
        processor.process(MESSAGE, TENANT);

        verify(lepManagementService).beginThreadContext();
        verify(configurationService).updateConfiguration(argThat(configWith(PATH, CONTENT)), argThat(eqString(OLD_CONFIG_HASH)));
    }

    @Test
    public void process_throwConcurrentConfigModificationException() {
        doThrow(new ConcurrentConfigModificationException()).when(configurationService)
            .updateConfiguration(argThat(configWith(PATH, CONTENT)), argThat(eqString(OLD_CONFIG_HASH)));

        assertThrows(ConcurrentConfigModificationException.class, () -> processor.process(MESSAGE, TENANT));

        verify(lepManagementService).beginThreadContext();
        verify(configurationService).updateConfiguration(argThat(configWith(PATH, CONTENT)), argThat(eqString(OLD_CONFIG_HASH)));
    }

    private ArgumentMatcher<String> eqString(String expected) {
        return expected::equals;
    }

    private ArgumentMatcher<Configuration> configWith(String path, String content) {
        return config -> config != null
            && path.equals(config.getPath())
            && content.equals(config.getContent());
    }
}
