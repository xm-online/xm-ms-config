package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.INMEMORY;
import static com.icthh.xm.ms.configuration.config.Constants.REFRESH;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.service.dto.ConfigurationHashSum;
import com.icthh.xm.ms.configuration.service.dto.ConfigurationsHashSumDto;
import lombok.SneakyThrows;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

@WithMockUser(authorities = {"SUPER-ADMIN"})
public class ConfigurationAdminResourceIntTest extends AbstractSpringBootTest {

    private static final String FULL_PATH_PREFIX = CONFIG + TENANTS;

    @MockBean
    private ConfigTopicProducer configTopicProducer;

    @Autowired
    private ConfigurationAdminResource configurationAdminResource;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private TenantContextHolder tenantContextHolder;

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
    public void testGetDocumentByVersion() {
        mockMvc.perform(post(API_PREFIX + CONFIG + TENANTS + "/test/version/file")
                                .content("1")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(put(API_PREFIX + CONFIG + TENANTS + "/test/version/file")
                                .content("2")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        String version = mockMvc.perform(get(API_PREFIX + "/version")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(put(API_PREFIX + CONFIG + TENANTS + "/test/version/file")
                                .content("3")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/test/version/file")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("3"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/test/version/file?version=" + version)
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("2"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testInMemoryUpdate() {
        mockMvc.perform(post(API_PREFIX + CONFIG + TENANTS + "/test/version/file")
                                .content("1")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(put(API_PREFIX + INMEMORY + CONFIG + TENANTS + "/test/version/file")
                                .content("2")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/test/version/file")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("2"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post(API_PREFIX + CONFIG + "/refresh"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/test/version/file")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("1"))
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

    @Test
    @SneakyThrows
    public void testDeleteMultipleDocuments() {
        mockMvc.perform(post(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname41")
                            .content("some content")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname42")
                            .content("some content")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname41")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(content().string("some content"))
               .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname42")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(content().string("some content"))
               .andExpect(status().is2xxSuccessful());

        mockMvc.perform(delete(API_PREFIX + CONFIG + TENANTS + "/test")
                            .content(
                                "[\"" + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname41\", "
                                + "\"" + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname42\"]")
                            .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname41")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().isNotFound());
        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname42")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().isNotFound());

    }

    @Test
    @SneakyThrows
    public void testDeleteInMemory() {
        createDocument("1");
        createDocument("2");
        createDocument("3");
        createDocument("4");
        verifyDocument("1");
        verifyDocument("2");
        verifyDocument("3");
        verifyDocument("4");


        mockMvc.perform(delete(API_PREFIX + INMEMORY + CONFIG + TENANTS + "/test")
                                .content(
                                        "[\"" + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname2\", "
                                        + "\"" + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname3\"]")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful());

        verifyDocument("1");
        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname2")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname3")
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound());
        verifyDocument("4");

    }

    private void verifyDocument(String s) throws Exception {
        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname" + s)
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(s))
                .andExpect(status().is2xxSuccessful());
    }

    private void createDocument(String placeholder) throws Exception {
        mockMvc.perform(put(API_PREFIX + INMEMORY + FULL_PATH_PREFIX + "/test/folder/subfolder/documentname" + placeholder)
                                .content(placeholder)
                                .contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testCreateMultipleFiles() {

        MockMultipartFile file1 = new MockMultipartFile("files",
                                                        FULL_PATH_PREFIX + "/test/folder/subfolder1/test1.txt",
                                                        "text/plain",
                                                        "test content1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files",
                                                        FULL_PATH_PREFIX + "/test/folder/subfolder2/test2.txt",
                                                        "text/plain",
                                                        "test content2".getBytes());
        mockMvc.perform(multipart(API_PREFIX + CONFIG)
                            .file(file1)
                            .file(file2))
               .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder1/test1.txt")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().is2xxSuccessful())
               .andExpect(content().string("test content1"));

        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder2/test2.txt")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().is2xxSuccessful())
               .andExpect(content().string("test content2"));
    }

    @Test
    @SneakyThrows
    public void testDeleteParentDirectory() {

        MockMultipartFile file1 = new MockMultipartFile("files",
                                                        FULL_PATH_PREFIX + "/test/folder/subfolder1/test1.json",
                                                        MediaType.APPLICATION_JSON_VALUE,
                                                        "test content1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files",
                                                        FULL_PATH_PREFIX + "/test/folder/subfolder2/test2.json",
                                                        MediaType.APPLICATION_JSON_VALUE,
                                                        "test content2".getBytes());
        mockMvc.perform(multipart(API_PREFIX + CONFIG)
                            .file(file1)
                            .file(file2))
               .andExpect(status().is2xxSuccessful());

        mockMvc.perform(delete(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder1/test1.json")
                            .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNotFound());

        mockMvc.perform(get(API_PREFIX + FULL_PATH_PREFIX + "/test/folder/subfolder2/test2.json")
                            .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNotFound());
    }

    @Test
    @SneakyThrows
    public void testDeleteTenantDirectory() {

        mockMvc.perform(post(API_PREFIX + CONFIG + TENANTS + "/TENANT1/folder/subfolder/documentname31")
                            .content("some content")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/TENANT1/folder/subfolder/documentname31")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(content().string("some content"))
               .andExpect(status().is2xxSuccessful());

        mockMvc.perform(delete(API_PREFIX + CONFIG + TENANTS + "/TENANT1")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/TENANT1/folder/subfolder/documentname31")
                            .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().isNotFound());
    }

    @Test
    @SneakyThrows
    public void testGetConfigurationHashSum() {
        String path = "/config/tenants/TENANT2/folder/subfolder/documentname5";
        String content = "some content";
        ConfigurationsHashSumDto response = new ConfigurationsHashSumDto();
        ConfigurationHashSum configurationHashSum = new ConfigurationHashSum(path, sha256Hex(content));
        response.setConfigurationsHashSum(List.of(configurationHashSum));

        mockMvc.perform(post(API_PREFIX + CONFIG + TENANTS + "/TENANT2/folder/subfolder/documentname5")
            .content(content)
            .contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(API_PREFIX + CONFIG + TENANTS + "/TENANT2/hash")
            .contentType(MediaType.TEXT_PLAIN))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
            .andExpect(content().json(new ObjectMapper().writeValueAsString(response)))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    @SneakyThrows
    public void testCheckAdminRefreshAvailable() {

        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
        TenantContextUtils.setTenant(tenantContextHolder, "SOME_TENANT");

        mockMvc.perform(get(API_PREFIX + CONFIG + REFRESH + "/available")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
            .andExpect(jsonPath("$.available").value("false"))
            .andExpect(status().is2xxSuccessful());

        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
        TenantContextUtils.setTenant(tenantContextHolder, "XM");

        mockMvc.perform(get(API_PREFIX + CONFIG + REFRESH + "/available")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
            .andExpect(jsonPath("$.available").value("true"))
            .andExpect(status().is2xxSuccessful());
    }
}
