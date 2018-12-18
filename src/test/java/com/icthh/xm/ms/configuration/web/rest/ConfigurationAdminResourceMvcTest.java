package com.icthh.xm.ms.configuration.web.rest;

import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static com.icthh.xm.ms.configuration.config.Constants.*;
import static org.mockito.Matchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = ConfigurationAdminResource.class, secure = false)
@ContextConfiguration(classes = {ConfigurationAdminResource.class})
public class ConfigurationAdminResourceMvcTest {

    @MockBean
    private ConfigurationService configurationService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @SneakyThrows
    public void ifPathPassedRefreshOnlyOneConfig() {
        String testPath = "/test/folder/subfolder/documentname";
        mockMvc.perform(post(API_PREFIX + CONFIG + REFRESH + testPath))
                .andExpect(status().is2xxSuccessful());

        Mockito.verify(configurationService).refreshConfiguration(eq(testPath));
        Mockito.verifyNoMoreInteractions(configurationService);
    }

    @Test
    @SneakyThrows
    public void ifPathNotPassedRefreshAll() {
        mockMvc.perform(post(API_PREFIX + CONFIG + REFRESH))
                .andExpect(status().is2xxSuccessful());

        Mockito.verify(configurationService).refreshConfiguration();
        Mockito.verifyNoMoreInteractions(configurationService);
    }
}
