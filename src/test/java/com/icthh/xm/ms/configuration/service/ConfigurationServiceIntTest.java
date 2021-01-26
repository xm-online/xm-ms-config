package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.icthh.xm.ms.configuration.service.TenantAliasService.TENANT_ALIAS_CONFIG;
import static com.icthh.xm.ms.configuration.web.rest.TestUtil.loadFile;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;

public class ConfigurationServiceIntTest extends AbstractSpringBootTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @MockBean
    ConfigTopicProducer configTopicProducer;

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    MemoryConfigStorage memoryConfigStorage;

    @Before
    public void before() {
        environmentVariables.clear("MAIN_valueForReplace", "LIFETENANT__valueForReplace");
        memoryConfigStorage.clear();
        loadTenantAliasConfig();
    }

    @Test
    public void testSimpleTenantAliasForPrivateApi() {
        Configuration mainValue = mockTenantConfig("MAIN", "mainValue");
        String content = mainValue.getContent();

        configurationService.updateConfiguration(mainValue);

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, filesList());
        doAssertions(() -> assertAllFromMain(content, privateMap));
    }

    @Test
    public void testSimpleTenantAliasForPublicApi() {
        Configuration mainValue = mockTenantConfig("MAIN", "mainValue");
        String content = mainValue.getContent();

        configurationService.updateConfiguration(mainValue);

        Map<String, Configuration> publicMap = getPromPublicApi();
        doAssertions(() -> assertAllFromMain(content, publicMap));
    }

    @Test
    public void testTenantAliasWithOverrideForPrivateApi() {
        Configuration mainValue = mockTenantConfig("MAIN", "mainValue");
        Configuration submainValue = mockTenantConfig("SUBMAIN", "submainValue");
        String mainContent = mainValue.getContent();
        String submainContent = submainValue.getContent();

        configurationService.updateConfiguration(submainValue);
        configurationService.updateConfiguration(mainValue);

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, filesList());
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

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, filesList());
        doAssertions(() -> {
            String mainExternalizations = mockTenantConfigWithExternalization("mainExternalizations");
            String lifetenantExternalizations = mockTenantConfigWithExternalization("lifetenantExternalizations");

            assertEquals(mainExternalizations, privateMap.get(pathInTenant("MAIN")).getContent());
            assertEquals(submainContent, privateMap.get(pathInTenant("SUBMAIN")).getContent());
            assertEquals(lifetenantExternalizations, privateMap.get(pathInTenant("LIFETENANT")).getContent());
            assertEquals(mainContent, privateMap.get(pathInTenant("ONEMORELIFETENANT")).getContent());
        });
    }

    private Map<String, Configuration> getPromPublicApi() {
        return filesList().stream().map(configurationService::findConfiguration)
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

    private void assertAllFromMain(String content, Map<String, Configuration> existsMap) {
        assertEquals(content, existsMap.get(pathInTenant("MAIN")).getContent());
        assertEquals(content, existsMap.get(pathInTenant("SUBMAIN")).getContent());
        assertEquals(content, existsMap.get(pathInTenant("LIFETENANT")).getContent());
        assertEquals(content, existsMap.get(pathInTenant("ONEMORELIFETENANT")).getContent());
    }

    private void assertWithSubmain(String mainContent, String submainContent, Map<String, Configuration> existsMap) {
        assertEquals(mainContent, existsMap.get(pathInTenant("MAIN")).getContent());
        assertEquals(submainContent, existsMap.get(pathInTenant("SUBMAIN")).getContent());
        assertEquals(submainContent, existsMap.get(pathInTenant("LIFETENANT")).getContent());
        assertEquals(mainContent, existsMap.get(pathInTenant("ONEMORELIFETENANT")).getContent());
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

    private String mockTenantConfigWithExternalization(String specialValue) {
        String tenantContent = loadFile("tenant-config-expected.yml");
        tenantContent = tenantContent.replace("VALUE_FOR_REPLACE", specialValue);
        return tenantContent;
    }

    private List<String> filesList() {
        return asList(
                pathInTenant("MAIN"),
                pathInTenant("SUBMAIN"),
                pathInTenant("LIFETENANT"),
                pathInTenant("ONEMORELIFETENANT")
        );
    }

    private String pathInTenant(String tenant) {
        return "/config/tenants/" + tenant + "/tenant-config.yml";
    }
}
