package com.icthh.xm.ms.configuration.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(authorities = {"SUPER-ADMIN"})
public class ConfigMapResourceMvcTest extends AbstractSpringBootTest {

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @MockBean
    private ConfigurationService configurationService;

    @MockBean
    private ApplicationProperties applicationProperties;

    private MockMvc restTaskMockMvc;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        this.restTaskMockMvc = MockMvcBuilders.standaloneSetup(new ConfigMapResource(configurationService, applicationProperties))
            .setControllerAdvice(exceptionTranslator)
            .build();
    }

    @Test
    @SneakyThrows
    public void getConfigMap() {
        when(configurationService.getConfigurationMap(null))
            .thenReturn(Collections.singletonMap("path", new Configuration("path", "content")));

        restTaskMockMvc.perform(get("/api/private/config_map")
                .contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path.path").value("path"))
            .andExpect(jsonPath("$.path.content").value("content"));
    }

    @Test
    @SneakyThrows
    public void getConfigMapWithCommit() {
        when(configurationService.getConfigurationMap("commit1"))
            .thenReturn(Collections.singletonMap("path", new Configuration("path", "content")));

        restTaskMockMvc.perform(get("/api/private/config_map?version={commit}", "commit1")
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
        when(configurationService.getConfigurationMap("commit1", asList("path")))
            .thenReturn(Collections.singletonMap("path", new Configuration("path", "content")));

        restTaskMockMvc.perform(post("/api/private/config_map", "commit1")
                .content(toJson(new GetConfigRequest(asList("path"), "commit1")))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path.path").value("path"))
            .andExpect(jsonPath("$.path.content").value("content"));
    }

    @Test
    @SneakyThrows
    public void updateConfigMapWithCommit() {
        when(applicationProperties.isUpdateConfigAvailable()).thenReturn(true);

        restTaskMockMvc.perform(put("/api/private/config?oldConfigHash={oldConfigHash}", "someHash").content(toJson(new Configuration("somePath", "some content")))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        verify(configurationService).updateConfiguration(refEq(new Configuration("somePath", "some content")), eq("someHash"));
    }

    private String toJson(Object object) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(object);
    }
}
