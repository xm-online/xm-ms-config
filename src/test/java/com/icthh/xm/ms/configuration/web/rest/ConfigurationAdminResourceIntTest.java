package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.icthh.xm.commons.exceptions.spring.web.ExceptionTranslator;
import com.icthh.xm.ms.configuration.ConfigurationApp;
import com.icthh.xm.ms.configuration.config.LocalJGitRepositoryConfiguration;
import com.icthh.xm.ms.configuration.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.ms.configuration.repository.kafka.SystemTopicProducer;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ConfigurationApp.class, SecurityBeanOverrideConfiguration.class, LocalJGitRepositoryConfiguration.class})
@WithMockUser(authorities = {"SUPER-ADMIN"})
public class ConfigurationAdminResourceIntTest {

    @MockBean
    private SystemTopicProducer systemTopicProducer;

    @Autowired
    private ConfigurationAdminResource configurationAdminResource;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(configurationAdminResource)
                .setControllerAdvice(exceptionTranslator)
                .build();
    }

    @Test
    @SneakyThrows
    public void testAddDocument() {
        mockMvc.perform(post(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname")
                    .content("some content")
                    .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("some content"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testUpdateDocument() {
        mockMvc.perform(post(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname2")
                .content("some content")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(put(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname2")
                .content("some content 2")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname2")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("some content 2"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testYmlJson() {
        mockMvc.perform(post(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname.yml")
                .content("field: \"field value\"")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname.yml?toJson")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.field").value("field value"));
    }

    @Test
    @SneakyThrows
    public void testDeleteDocument() {
        mockMvc.perform(post(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname3")
                .content("some content")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname3")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("some content"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(delete(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname3")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/test/folder/subfolder/documentname3")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound());
    }


}
