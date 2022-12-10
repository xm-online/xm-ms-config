package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.repository.DistributedConfigRepository;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.service.dto.ConfigurationHashSum;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import static com.icthh.xm.ms.configuration.service.TenantAliasService.TENANT_ALIAS_CONFIG;
import static com.icthh.xm.ms.configuration.web.rest.TestUtil.loadFile;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class ConfigurationServiceIntTest extends AbstractSpringBootTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @MockBean
    ConfigTopicProducer configTopicProducer;

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    MemoryConfigStorage memoryConfigStorage;

    @Autowired
    PersistenceConfigRepository repositoryProxy;

    @Before
    public void before() {
        environmentVariables.clear("MAIN_valueForReplace", "LIFETENANT__valueForReplace");
        memoryConfigStorage.clear();
        loadTenantAliasConfig();
    }

    @Test
    public void testLepProcessorForPrivateApi() {
        String path = "/config/tenants/LEPTENANT/folder/cool-config.yml";
        String content = "startContent:" + Instant.now();
        Configuration mainValue = new Configuration(path, content);

        configurationService.updateConfiguration(mainValue);

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, List.of(path));
        assertEquals("processed content of: " + content, privateMap.get(path).getContent());
    }

    @Test
    public void testSimpleTenantAliasForPrivateApi() {
        Configuration mainValue = mockTenantConfig("MAIN", "mainValue");
        String content = mainValue.getContent();

        configurationService.updateConfiguration(mainValue);

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, filesList("/tenant-config.yml"));
        doAssertions(() -> assertAllFromMain(content, privateMap, "/tenant-config.yml"));
    }

    @Test
    public void testSimpleTenantAliasForPublicApi() {
        Configuration mainValue = mockTenantConfig("MAIN", "mainValue");
        String content = mainValue.getContent();

        configurationService.updateConfiguration(mainValue);

        Map<String, Configuration> publicMap = getPromPublicApi();
        doAssertions(() -> assertAllFromMain(content, publicMap, "/tenant-config.yml"));
    }

    @Test
    public void testTenantAliasWithOverrideForPrivateApi() {
        Configuration mainValue = mockTenantConfig("MAIN", "mainValue");
        Configuration submainValue = mockTenantConfig("SUBMAIN", "submainValue");
        String mainContent = mainValue.getContent();
        String submainContent = submainValue.getContent();

        configurationService.updateConfiguration(submainValue);
        configurationService.updateConfiguration(mainValue);

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, filesList("/tenant-config.yml"));
        doAssertions(() -> assertWithSubmain(mainContent, submainContent, privateMap));
    }

    @Test
    public void testTenantAliasWithOverrideForPublicApi() {
        Configuration mainValue = mockTenantConfig("MAIN", "mainValue");
        Configuration submainValue = mockTenantConfig("SUBMAIN", "submainValue");
        String mainContent = mainValue.getContent();
        String submainContent = submainValue.getContent();

        configurationService.updateConfiguration(mainValue);
        configurationService.updateConfiguration(submainValue);

        Map<String, Configuration> publicMap = getPromPublicApi();
        doAssertions(() -> assertWithSubmain(mainContent, submainContent, publicMap));
    }

    @Test
    public void testTenantExternalizationInAliasTenant() {
        environmentVariables.set("MAIN_valueForReplace", "mainExternalizations");
        environmentVariables.set("LIFETENANT_valueForReplace", "lifetenantExternalizations");

        Configuration mainValue = mockTenantConfig("MAIN", "mainValue");
        Configuration submainValue = mockTenantConfig("SUBMAIN", "submainValue");
        String mainContent = mainValue.getContent();
        String submainContent = submainValue.getContent();

        configurationService.updateConfiguration(mainValue);
        configurationService.updateConfiguration(submainValue);

        Map<String, Configuration> publicMap = getPromPublicApi();
        doAssertions(() -> assertWithSubmain(mainContent, submainContent, publicMap));

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, filesList("/tenant-config.yml"));
        doAssertions(() -> {
            String mainExternalizations = mockTenantConfigWithExternalization("mainExternalizations");
            String lifetenantExternalizations = mockTenantConfigWithExternalization("lifetenantExternalizations");

            assertEquals(mainExternalizations, privateMap.get(pathInTenant("MAIN", "/tenant-config.yml")).getContent());
            assertEquals(submainContent, privateMap.get(pathInTenant("SUBMAIN", "/tenant-config.yml")).getContent());
            assertEquals(lifetenantExternalizations, privateMap.get(pathInTenant("LIFETENANT", "/tenant-config.yml")).getContent());
            assertEquals(mainContent, privateMap.get(pathInTenant("ONEMORELIFETENANT", "/tenant-config.yml")).getContent());
        });
    }

    @Test
    public void testSimpleTenantAliasForPrivateApiWhenMemoryUpdate() {
        Mockito.reset(configTopicProducer);
        Configuration mainValue = mockConfig("MAIN", "mainValue", "/some-config.yml");
        String content = mainValue.getContent();

        configurationService.updateConfigurationInMemory(mainValue);

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, filesList("/some-config.yml"));
        doAssertions(() -> assertAllFromMain(content, privateMap, "/some-config.yml"));
        verify(configTopicProducer).notifyConfigurationChanged(anyString(), eq(filesList("/some-config.yml")));
    }

    @Test
    public void testExcludeFileFromNotifications() {
        List<String> paths = repositoryProxy.findAll()
                .getData()
                .stream()
                .map(Configuration::getPath)
                .collect(toList());

        configurationService.deleteConfigurations(paths);
        memoryConfigStorage.clear();

        configurationService.updateConfiguration(new Configuration("/config/file1", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/file2", "2\n"));
        configurationService.updateConfiguration(new Configuration("/config/excluded/file", "3\n"));

        Mockito.reset(configTopicProducer);

        configurationService.refreshConfiguration();
        verify(configTopicProducer).notifyConfigurationChanged(anyString(), eq(List.of(
                "/config/file2", "/config/file1"
        )));
    }

    @Test
    @SneakyThrows
    public void testUpdateFromZipFile() {
        configurationService.updateConfiguration(new Configuration("/config/file1", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/file2", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/file3", "1\n"));
        String commit = configurationService.updateConfigurationsFromZip(
                new MockMultipartFile("testrepo1.zip", new ClassPathResource("testrepo1.zip").getInputStream()));
        Map<String, Configuration> configurationMap = configurationService.getConfigurationMap(commit);
        assertEquals(configurationMap, Map.of(
                "/config/file1", new Configuration("/config/file1", "1\n"),
                "/config/file2", new Configuration("/config/file2", "2\n")
        ));
    }

    @Test
    @SneakyThrows
    public void testExcludeConfig() {
        memoryConfigStorage.clear();
        configurationService.updateConfiguration(new Configuration("/config/file1", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/file2", "2\n"));
        configurationService.updateConfiguration(new Configuration("/config/excluded/file", "3\n"));

        Map<String, Configuration> configurationMap = configurationService.getConfigurationMap(null);
        assertEquals(configurationMap, Map.of(
                "/config/file1", new Configuration("/config/file1", "1\n"),
                "/config/file2", new Configuration("/config/file2", "2\n")
        ));
    }

    private Map<String, Configuration> getPromPublicApi() {
        return filesList("/tenant-config.yml").stream().map(configurationService::findConfiguration)
                .filter(Optional::isPresent).map(Optional::get)
                .collect(toMap(Configuration::getPath, identity()));
    }

    private void doAssertions(Runnable assertion) {
        assertion.run();
        configurationService.refreshConfiguration();
        assertion.run();
        configurationService.refreshTenantConfigurations("MAIN");
        assertion.run();
        configurationService.refreshTenantConfigurations("SUBMAIN");
        assertion.run();
        configurationService.refreshTenantConfigurations("LIFETENANT");
        assertion.run();
        configurationService.refreshTenantConfigurations("ONEMORELIFETENANT");
        assertion.run();
    }

    private void assertAllFromMain(String content, Map<String, Configuration> existsMap, String name) {
        assertEquals(content, existsMap.get(pathInTenant("MAIN", name)).getContent());
        assertEquals(content, existsMap.get(pathInTenant("SUBMAIN", name)).getContent());
        assertEquals(content, existsMap.get(pathInTenant("LIFETENANT", name)).getContent());
        assertEquals(content, existsMap.get(pathInTenant("ONEMORELIFETENANT", name)).getContent());
    }

    private void assertWithSubmain(String mainContent, String submainContent, Map<String, Configuration> existsMap) {
        String name = "/tenant-config.yml";
        assertEquals(mainContent, existsMap.get(pathInTenant("MAIN", name)).getContent());
        assertEquals(submainContent, existsMap.get(pathInTenant("SUBMAIN", name)).getContent());
        assertEquals(submainContent, existsMap.get(pathInTenant("LIFETENANT", name)).getContent());
        assertEquals(mainContent, existsMap.get(pathInTenant("ONEMORELIFETENANT", name)).getContent());
    }

    private void loadTenantAliasConfig() {
        Configuration configuration = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree.yml"));
        configurationService.updateConfigurationInMemory(configuration);
    }

    private Configuration mockTenantConfig(String tenant, String specialValue) {
        String tenantContent = loadFile("tenant-config.yml");
        tenantContent = tenantContent.replace("VALUE_FOR_REPLACE", specialValue);
        return new Configuration("/config/tenants/" + tenant + "/tenant-config.yml", tenantContent);
    }

    private Configuration mockConfig(String tenant, String specialValue, String name) {
        String tenantContent = loadFile("some-config.yml");
        tenantContent = tenantContent.replace("VALUE_FOR_REPLACE", specialValue);
        return new Configuration("/config/tenants/" + tenant + name, tenantContent);
    }

    private String mockTenantConfigWithExternalization(String specialValue) {
        String tenantContent = loadFile("tenant-config-expected.yml");
        tenantContent = tenantContent.replace("VALUE_FOR_REPLACE", specialValue);
        return tenantContent;
    }

    private List<String> filesList(String name) {
        // hash set for correct order in list
        return new ArrayList<>(new HashSet<>(
                List.of(
                        pathInTenant("MAIN", name),
                        pathInTenant("SUBMAIN", name),
                        pathInTenant("LIFETENANT", name),
                        pathInTenant("ONEMORELIFETENANT", name)
                )
        ));
    }

    private String pathInTenant(String tenant, String name) {
        return "/config/tenants/" + tenant + name;
    }

}
