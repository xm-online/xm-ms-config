package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.PROFILE;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;
import static com.icthh.xm.ms.configuration.service.TenantAliasService.TENANT_ALIAS_CONFIG;
import static com.icthh.xm.ms.configuration.utils.RequestContextUtils.OLD_CONFIG_HASH;
import static com.icthh.xm.ms.configuration.web.rest.TestUtil.loadFile;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.service.TenantAliasService;
import lombok.SneakyThrows;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

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

    @Autowired
    private TenantAliasService tenantAliasService;

    @Autowired
    ConfigurationService configurationService;

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
    public void testWebappPublicConfigExternalizationFromTenantProfile() {
        mockMvc.perform(post(API_PREFIX + PROFILE + "/tenant-profile.yml")
                        .content("---\nenvironment:\n  VARIABLE_FOR_REPLACE_FROM_TENANT_PROFILE: expectedValue")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post(API_PREFIX + PROFILE + "/webapp/settings-public.yml")
            .content("varForReplaceFromTenantProfile: ${environment.VARIABLE_FOR_REPLACE_FROM_TENANT_PROFILE}")
            .contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + PROFILE + "/webapp/settings-public.yml?toJson&processed=true")
                        .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("{\"varForReplaceFromTenantProfile\":\"expectedValue\"}"))
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
    public void testGetConfigurationsByPaths() {
        String firstPath = CONFIG + TENANTS + "/TENANT1/documentname1";
        String secondPath = CONFIG + TENANTS + "/TENANT1/documentname2";
        String firstContent = "first content";
        String secondContent = "second content";

        mockMvc.perform(post(API_PREFIX + firstPath)
                .content(firstContent)
                .contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().is2xxSuccessful());

        mockMvc.perform(post(API_PREFIX + secondPath)
                .content(secondContent)
                .contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().is2xxSuccessful());

        mockMvc.perform(post(API_PREFIX + PROFILE + "/configs_map")
            .content(new ObjectMapper().writeValueAsString(List.of(firstPath, secondPath)))
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
            .andExpect(jsonPath("$..path").value(Matchers.containsInAnyOrder(firstPath,secondPath)))
            .andExpect(jsonPath("$..content").value(Matchers.containsInAnyOrder(firstContent,secondContent)));
    }

    @Test
    @SneakyThrows
    public void testGetTreeConfigurationsByPaths() {
        Configuration config = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree.yml"));
        tenantAliasService.processConfiguration(config, Map.of(), Map.of());

        String path = CONFIG + TENANTS + "/MAIN/my-config.yml";
        String path2 = CONFIG + TENANTS + "/LIFETENANT/my-config.yml";
        String content = "my cool config";
        Configuration mainValue = new Configuration(path, content);

        configurationService.updateConfiguration(mainValue);

        mockMvc.perform(post(API_PREFIX + PROFILE + "/configs_map")
                .param("fetchAll", "false")
                .content(new ObjectMapper().writeValueAsString(List.of(path2)))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$..content").value(Matchers.contains(content)));
    }

    @Test
    @SneakyThrows
    public void testGetConfigurationsHashSum() {
        String path = CONFIG + TENANTS + "/"+TENANT_NAME+"/folder/subfolder/documentname";
        String content = "very cool content";

        configurationService.updateConfiguration(new Configuration(path, content));

        mockMvc.perform(get(API_PREFIX + PROFILE + "/configs_hash")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andDo(print())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
            .andExpect(jsonPath("$..hashSum").value(Matchers.notNullValue()));
    }

    @Test
    @SneakyThrows
    public void updateTenantsFromJson() {
        String path = CONFIG + TENANTS + "/TENANT1/my-config.yml";
        String path2 = CONFIG + TENANTS + "/TENANT2/my-config.yml";
        String contentToUpdate = "very cool content to update";
        String contentToDelete = "very cool content to delete";
        String updatedContent = "very cool updated content";
        Configuration updatedConfiguration = new Configuration(path, updatedContent);
        Configuration wrongConfiguration = new Configuration(path, "wrong content");

        configurationService.updateConfiguration(new Configuration(path, contentToUpdate));
        configurationService.updateConfiguration(new Configuration(path2, contentToDelete));

        mockMvc.perform(post(API_PREFIX + PROFILE + "/configs_update")
                .content(new ObjectMapper().writeValueAsString(List.of(updatedConfiguration, new Configuration(path2, ""))))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful());

        Map<String, Configuration> configurationMap = configurationService.findConfigurations(List.of(), true);
        assertTrue(configurationMap.get(path).getContent().equals(updatedContent));
        assertFalse(configurationMap.containsKey(path2));
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
