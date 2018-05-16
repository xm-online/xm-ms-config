package com.icthh.xm.ms.configuration.web.rest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icthh.xm.commons.config.client.api.ConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = ConfigMapResource.class)
@ContextConfiguration(classes = ConfigMapResource.class)
@WithMockUser(authorities = {"SUPER-ADMIN"})
public class ConfigMapResourceMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ConfigService configService;

    @Test
    @SneakyThrows
    public void getConfigMap() {
        when(configService.getConfigurationMap(null))
            .thenReturn(Collections.singletonMap("path", new Configuration("path", "content")));

        mockMvc.perform(get("/api/config_map")
            .contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path.path").value("path"))
            .andExpect(jsonPath("$.path.content").value("content"));
    }

    @Test
    @SneakyThrows
    public void getConfigMapWithCommit() {
        when(configService.getConfigurationMap("commit1"))
            .thenReturn(Collections.singletonMap("path", new Configuration("path", "content")));

        mockMvc.perform(get("/api/config_map?commit={commit}", "commit1")
            .contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path.path").value("path"))
            .andExpect(jsonPath("$.path.content").value("content"));
    }
}
