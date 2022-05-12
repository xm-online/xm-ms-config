package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.PROFILE;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.OLD_CONFIG_HASH;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@WithMockUser(authorities = {"SUPER-ADMIN"})
@TestPropertySource(properties = "application.env-config-externalization-enabled=true")
public class ConfigurationClientResourceIntTest extends AbstractSpringBootTest {

    public static final String TENANT_NAME = "test75";

    @MockBean
    private ConfigTopicProducer configTopicProducer;

    @ClassRule
    public static EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Autowired
    private ConfigurationClientResource configurationClientResource;

    @Autowired
    private ConfigurationAdminResource configurationAdminResource;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(configurationClientResource, configurationAdminResource)
                .setControllerAdvice(exceptionTranslator)
                .build();
        TenantContextUtils.setTenant(tenantContextHolder, TENANT_NAME);
    }

    @BeforeClass
    public static void beforeClass () {
        environmentVariables.set("VARIABLE_FOR_REPLACE", "expectedValue");
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
    public void testUpdateDocumentWithHash() {
        String content = "some content";
        mockMvc.perform(post(API_PREFIX + PROFILE + "/folder/subfolder/documentname2")
                .content(content)
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(put(API_PREFIX + PROFILE + "/folder/subfolder/documentname2?" + OLD_CONFIG_HASH + "=" + sha1Hex(content))
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
    public void testUpdateDocumentWithUncorrectHash() {
        String content = "some content";
        mockMvc.perform(post(API_PREFIX + PROFILE + "/folder/subfolder/documentname2")
                .content(content)
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(put(API_PREFIX + PROFILE + "/folder/subfolder/documentname2?" + OLD_CONFIG_HASH + "=uncorrectHash")
                .content("some content 2")
                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isConflict());
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

    @Test
    @SneakyThrows
    public void testWebappPublicConfigExternalization() {
        environmentVariables.set("VARIABLE_FOR_REPLACE", "expectedValue");
        mockMvc.perform(post(API_PREFIX + PROFILE + "/webapp/settings-public.yml")
                        .content("varForReplace: ${environment.VARIABLE_FOR_REPLACE}")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/webapp/settings-public.yml?toJson&processed=true")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("{\"varForReplace\":\"expectedValue\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testWebappPrivateConfigExternalization() {
        environmentVariables.set("VARIABLE_FOR_REPLACE", "expectedValue");
        mockMvc.perform(post(API_PREFIX + PROFILE + "/webapp/settings-private.yml")
                        .content("varForReplace: ${environment.VARIABLE_FOR_REPLACE}")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/webapp/settings-private.yml?toJson&processed=true")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("{\"varForReplace\":\"expectedValue\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testPublicWebappConfigExternalization() {
        environmentVariables.set("VARIABLE_FOR_REPLACE", "expectedValue");
        mockMvc.perform(post(API_PREFIX + PROFILE + "/webapp/public/config.yml")
                        .content("varForReplace: ${environment.VARIABLE_FOR_REPLACE}")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/webapp/public/config.yml?toJson&processed=true")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("{\"varForReplace\":\"expectedValue\"}"))
                .andExpect(status().is2xxSuccessful());
    }


    @Test
    @SneakyThrows
    public void authorizedUserWithPermissionCanGetPrivateWebConfig() {
        mockMvc.perform(post(API_PREFIX + PROFILE + "/webapp/settings-private.yml")
                            .content("some: content")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/webapp/settings-private.yml?toJson")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(content().string("{\"some\":\"content\"}"))
               .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void getDefaultPrivateSettingIfNotExists() {
        mockMvc.perform(delete(API_PREFIX + PROFILE + "/webapp/settings-private.yml"))
               .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/webapp/settings-private.yml?toJson")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    @WithMockUser(authorities = {"ANONYMOUS"})
    public void notAuthorizedUserCanNotGetPrivateWebConfig() {
        mockMvc.perform(get(API_PREFIX + PROFILE + "/webapp/settings-private.yml?toJson")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().isForbidden());
    }


}
