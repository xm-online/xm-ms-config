package com.icthh.xm.ms.configuration.web.rest;

import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.RECLONE;
import static com.icthh.xm.ms.configuration.config.Constants.REFRESH;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConfigurationAdminResourceMvcIntTest extends AbstractSpringBootTest {

    @MockBean
    private ConfigurationService configurationService;

    private MockMvc restTaskMockMvc;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        this.restTaskMockMvc = MockMvcBuilders.standaloneSetup(new ConfigurationAdminResource(configurationService))
            .build();
    }

    @Test
    @SneakyThrows
    public void ifPathPassedRefreshOnlyOneConfig() {
        String testPath = "/test/folder/subfolder/documentname";
        restTaskMockMvc.perform(post(API_PREFIX + CONFIG + REFRESH + testPath))
            .andExpect(status().is2xxSuccessful());

        Mockito.verify(configurationService).assertAdminRefreshAvailable();
        Mockito.verify(configurationService).refreshConfiguration(eq(testPath));
        Mockito.verifyNoMoreInteractions(configurationService);
    }

    @Test
    @SneakyThrows
    public void ifPathNotPassedRefreshAll() {
        restTaskMockMvc.perform(post(API_PREFIX + CONFIG + REFRESH))
            .andExpect(status().is2xxSuccessful());

        Mockito.verify(configurationService).assertAdminRefreshAvailable();
        Mockito.verify(configurationService).refreshConfiguration();
        Mockito.verifyNoMoreInteractions(configurationService);
    }


    @Test
    @SneakyThrows
    public void testRecloneConfiguration() {
        restTaskMockMvc.perform(post(API_PREFIX + CONFIG + RECLONE))
            .andExpect(status().is2xxSuccessful());

        verify(configurationService).recloneConfiguration();
        verifyNoMoreInteractions(configurationService);
    }
}
