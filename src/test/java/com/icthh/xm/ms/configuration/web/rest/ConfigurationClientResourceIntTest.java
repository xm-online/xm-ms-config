package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.icthh.xm.commons.errors.ExceptionTranslator;
import com.icthh.xm.ms.configuration.ConfigurationApp;
import com.icthh.xm.ms.configuration.config.LocalJGitRespotioryConfiguration;
import com.icthh.xm.ms.configuration.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.ms.configuration.config.tenant.TenantContext;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ConfigurationApp.class, SecurityBeanOverrideConfiguration.class, LocalJGitRespotioryConfiguration.class})
public class ConfigurationClientResourceIntTest {

    public static final String TENANT_NAME = "test75";

    @Autowired
    private ConfigurationClientResource configurationClientResource;

    @Autowired
    private ConfigurationAdminResource configurationAdminResource;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(configurationClientResource, configurationAdminResource)
                .setControllerAdvice(exceptionTranslator)
                .build();
        TenantContext.setCurrent(TENANT_NAME);
    }

    @Test
    @SneakyThrows
    public void testAddSetTenantPath() {
        mockMvc.perform(post(API_PREFIX + PROFILE + "/folder/subfolder/documentname")
                .content("some content 78342578956234789562378946589237465892346576")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/" + TENANT_NAME + "/folder/subfolder/documentname")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("some content 78342578956234789562378946589237465892346576"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testAddDocument() {
        mockMvc.perform(post(API_PREFIX + PROFILE + "/folder/subfolder/documentname")
                    .content("some content")
                    .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/folder/subfolder/documentname")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("some content"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testUpdateDocument() {
        mockMvc.perform(post(API_PREFIX + PROFILE + "/folder/subfolder/documentname2")
                .content("some content")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(put(API_PREFIX + PROFILE + "/folder/subfolder/documentname2")
                .content("some content 2")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/folder/subfolder/documentname2")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("some content 2"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testYmlJson() {
        mockMvc.perform(post(API_PREFIX + PROFILE + "/folder/subfolder/documentname.yml")
                .content("field: \"field value\"")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/folder/subfolder/documentname.yml?toJson")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("$.field").value("field value"));
    }

    @Test
    @SneakyThrows
    public void testDeleteDocument() {
        mockMvc.perform(post(API_PREFIX + PROFILE + "/folder/subfolder/documentname3")
                .content("some content")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/folder/subfolder/documentname3")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("some content"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(delete(API_PREFIX + PROFILE + "/folder/subfolder/documentname3")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/folder/subfolder/documentname3")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound());
    }


}
