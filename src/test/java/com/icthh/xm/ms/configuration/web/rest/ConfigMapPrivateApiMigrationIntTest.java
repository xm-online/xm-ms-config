package com.icthh.xm.ms.configuration.web.rest;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the profile migration the old private API works without authorization, as before.
 */
@ActiveProfiles("migration")
public class ConfigMapPrivateApiMigrationIntTest extends AbstractSpringBootTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ConfigurationService configurationService;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(configurationService.getConfigurationMap(any()))
            .thenReturn(Map.of("path", new Configuration("path", "content")));
    }

    @Test
    @SneakyThrows
    public void testOldApiWorksWithoutToken() {
        mockMvc.perform(get("/api/private/config_map")).andExpect(status().isOk());
    }

    @Test
    @SneakyThrows
    public void testNewApiStillRequiresToken() {
        mockMvc.perform(get("/api/private/v2/config_map")).andExpect(status().is4xxClientError());
    }
}
