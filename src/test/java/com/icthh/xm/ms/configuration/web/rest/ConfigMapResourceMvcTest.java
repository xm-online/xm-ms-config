package com.icthh.xm.ms.configuration.web.rest;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.config.client.api.ConfigService;
import com.icthh.xm.commons.config.domain.Configuration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
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
import java.util.List;

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

        mockMvc.perform(get("/api/private/config_map")
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

        mockMvc.perform(get("/api/private/config_map?version={commit}", "commit1")
            .contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path.path").value("path"))
            .andExpect(jsonPath("$.path.content").value("content"));
    }

    @Getter
    @AllArgsConstructor
    private static class GetConfigRequest {
        private List<String> paths;
        private String version;
    }

    @Test
    @SneakyThrows
    public void getConfigMapFilteredWithCommit() {
        when(configService.getConfigurationMap("commit1", asList("path")))
                .thenReturn(Collections.singletonMap("path", new Configuration("path", "content")));

        mockMvc.perform(post("/api/private/config_map", "commit1").content(new ObjectMapper().writeValueAsString(new GetConfigRequest(asList("path"), "commit1")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path.path").value("path"))
                .andExpect(jsonPath("$.path.content").value("content"));
    }
}
