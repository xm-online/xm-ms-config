package com.icthh.xm.ms.configuration.web.rest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.ms.configuration.ConfigurationApp;
import com.icthh.xm.ms.configuration.config.LocalJGitRepositoryConfiguration;
import com.icthh.xm.ms.configuration.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.ms.configuration.domain.TenantState;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
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

import java.util.Set;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ConfigurationApp.class, SecurityBeanOverrideConfiguration.class, LocalJGitRepositoryConfiguration.class})
@WithMockUser(authorities = {"SUPER-ADMIN"})
public class TenantResourceIntTest {

    @MockBean
    private ConfigTopicProducer configTopicProducer;

    @Autowired
    private TenantResource tenantResource;

    @Autowired
    private ConfigurationAdminResource configurationAdminResource;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(tenantResource, configurationAdminResource)
                .setControllerAdvice(exceptionTranslator)
                .build();
    }

    @Test
    @SneakyThrows
    public void testAddTenants() {
        ObjectMapper om = new ObjectMapper();

        mockMvc.perform(post("/api/tenants/entity")
                    .content("xm")
                    .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/tenants/entity")
                .content("test1")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/tenants/entity")
                .content("demo")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/tenants/entity")
                .content("olololo")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/tenants/entity")
                .content("test2")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/tenants/entity")
                .contentType(MediaType.TEXT_PLAIN))
                .andDo(mvc -> {
                    CollectionType stringSet = om.getTypeFactory().constructCollectionType(Set.class, TenantState.class);
                    Set<TenantState> value = om.readValue(mvc.getResponse().getContentAsString(), stringSet);
                    assertTrue(value.contains(new TenantState("xm","")));
                    assertTrue(value.contains(new TenantState("test1", "")));
                    assertTrue(value.contains(new TenantState("demo", "")));
                    assertTrue(value.contains(new TenantState("olololo", "")));
                    assertTrue(value.contains(new TenantState("test2", "")));
                    assertFalse(value.contains(new TenantState("test123123", "")));
                })
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testDeleteTenants() {
        ObjectMapper om = new ObjectMapper();

        mockMvc.perform(post("/api/tenants/entity")
                .content("xm")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/tenants/entity")
                .content("test1")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/tenants/entity")
                .content("test2")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/tenants/entity")
                .contentType(MediaType.TEXT_PLAIN))
                .andDo(mvc -> {
                    CollectionType stringSet = om.getTypeFactory().constructCollectionType(Set.class, TenantState.class);
                    Set<TenantState> value = om.readValue(mvc.getResponse().getContentAsString(), stringSet);
                    assertTrue(value.contains(new TenantState("xm", "")));
                    assertTrue(value.contains(new TenantState("test1", "")));
                    assertTrue(value.contains(new TenantState("test2", "")));
                })
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(delete("/api/tenants/entity/test1"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/tenants/entity")
                .contentType(MediaType.TEXT_PLAIN))
                .andDo(mvc -> {
                    CollectionType stringSet = om.getTypeFactory().constructCollectionType(Set.class, TenantState.class);
                    Set<TenantState> value = om.readValue(mvc.getResponse().getContentAsString(), stringSet);
                    assertTrue(value.contains(new TenantState("xm", "")));
                    assertTrue(value.contains(new TenantState("test2", "")));
                });
    }


}
