package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.repository.impl.JGitRepository;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.web.rest.TestUtil;
import java.util.Collection;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.AntPathMatcher;

import static com.icthh.xm.ms.configuration.service.TenantAliasTreeService.TENANT_ALIAS_CONFIG;
import static com.icthh.xm.ms.configuration.web.rest.TestUtil.loadFile;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    TenantAliasRefreshableConfiguration tenantAliasRefreshableConfiguration;

    @Autowired
    JGitRepository repository;

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

        configurationService.updateConfigurationInMemory(List.of(mainValue));

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, filesList("/some-config.yml"));
        verify(configTopicProducer).notifyConfigurationChanged(any(ConfigVersion.class), eq(filesList("/some-config.yml")));
        doAssertions(() -> assertAllFromMain(content, privateMap, "/some-config.yml"));
    }

    @Test
    public void testExcludeFileFromNotifications() {
        List<String> paths = repository.findAll()
                .getData()
                .stream()
                .map(Configuration::getPath)
                .collect(toList());

        configurationService.deleteConfigurations(paths);
        memoryConfigStorage.clear();

        configurationService.updateConfiguration(new Configuration("/config/tenants/file1", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/tenants/file2", "2\n"));
        configurationService.updateConfiguration(new Configuration("/config/tenants/excluded/file", "3\n"));

        Mockito.reset(configTopicProducer);

        configurationService.refreshConfiguration();
        verify(configTopicProducer).notifyConfigurationChanged(any(ConfigVersion.class), argThat(a -> a.containsAll(List.of(
            "/config/tenants/file2", "/config/tenants/file1"
        ))));
    }

    @Test
    @SneakyThrows
    public void testUpdateFromZipFile() {
        configurationService.updateConfiguration(new Configuration("/config/tenants/file1", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/tenants/file2", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/tenants/file3", "1\n"));
        ConfigVersion commit = configurationService.updateConfigurationsFromZip(
                new MockMultipartFile("testrepo1.zip", new ClassPathResource("testrepo1.zip").getInputStream()));
        String version = new ObjectMapper().writeValueAsString(commit);
        Map<String, Configuration> configurationMap = configurationService.getConfigurationMap(version);
        assertEquals(Map.of(
            "/config/tenants/file1", new Configuration("/config/tenants/file1", "1\n"),
            "/config/tenants/file2", new Configuration("/config/tenants/file2", "2\n")
        ), configurationMap);
    }

    @Test
    @SneakyThrows
    public void testExcludeConfig() {
        memoryConfigStorage.clear();
        configurationService.updateConfiguration(new Configuration("/config/tenants/file1", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/tenants/file2", "2\n"));
        configurationService.updateConfiguration(new Configuration("/config/tenants/excluded/file", "3\n"));

        Map<String, Configuration> configurationMap = configurationService.getConfigurationMap(null);
        assertEquals(configurationMap, Map.of(
                "/config/tenants/file1", new Configuration("/config/tenants/file1", "1\n"),
                "/config/tenants/file2", new Configuration("/config/tenants/file2", "2\n")
        ));
    }

    @Test
    @SneakyThrows
    public void testNonTenantFilesOutOfScope() {
        memoryConfigStorage.clear();
        configurationService.updateConfiguration(new Configuration("/config/tenants/file1", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/tenants/file2", "2\n"));
        configurationService.updateConfiguration(new Configuration("/config/file3", "3\n"));
        configurationService.updateConfiguration(new Configuration("/config/dir/file4", "4\n"));

        Map<String, Configuration> configurationMap = configurationService.getConfigurationMap(null);
        assertEquals(configurationMap, Map.of(
            "/config/tenants/file1", new Configuration("/config/tenants/file1", "1\n"),
            "/config/tenants/file2", new Configuration("/config/tenants/file2", "2\n")
        ));
    }

    @Test
    @SneakyThrows
    public void testUpdateExternalFiles() {
        memoryConfigStorage.clear();
        configurationService.updateConfiguration(new Configuration("/config/tenants/file1", "1\n"));
        configurationService.updateConfiguration(new Configuration("/config/tenants/file2", "2\n"));
        configurationService.updateConfiguration(new Configuration("/config/file3", "3\n"));
        configurationService.updateConfiguration(new Configuration("/config/dir/file4", "4\n"));
        configurationService.updateConfiguration(new Configuration("/config/smth/file5", "5\n"));
        configurationService.updateConfiguration(new Configuration("/config/smth/file6", "6\n"));
        configurationService.updateConfiguration(new Configuration("/config/smth/file7", "7\n"));

        Set<Configuration> configurations = configurationService.findExternalConfiguration("smth");
        assertEquals(Set.of(
            new Configuration("/config/smth/file5", "5\n"),
            new Configuration("/config/smth/file6", "6\n"),
            new Configuration("/config/smth/file7", "7\n")
        ), configurations);

        configurationService.updateConfiguration(new Configuration("/config/smth/file6", ""));
        Set<Configuration> updated = configurationService.findExternalConfiguration("smth");
        assertEquals(Set.of(
            new Configuration("/config/smth/file5", "5\n"),
            new Configuration("/config/smth/file7", "7\n")
        ), updated);

        Optional<Configuration> file3 = configurationService.findConfiguration("/config/file3");
        assertTrue(file3.isPresent());
        assertEquals("3\n", file3.get().getContent());

        Optional<Configuration> file4 = configurationService.findConfiguration("/config/dir/file4");
        assertTrue(file4.isPresent());
        assertEquals("4\n", file4.get().getContent());
    }

    @Test
    @SneakyThrows
    public void testUpdateExternalFilesDuringFullRefresh() {
        memoryConfigStorage.clear();
        repository.saveAll(List.of(
            new Configuration("/config/tenants/file1", "1\n"),
            new Configuration("/config/tenants/file2", "2\n"),
            new Configuration("/config/file3", "3\n"),
            new Configuration("/config/dir/file4", "4\n"),
            new Configuration("/config/smth/file5", "5\n"),
            new Configuration("/config/smth/file6", "6\n"),
            new Configuration("/config/smth/file7", "7\n")
        ), Map.of());
        configurationService.refreshConfiguration();

        Set<Configuration> configurations = configurationService.findExternalConfiguration("smth");
        assertEquals(Set.of(
            new Configuration("/config/smth/file5", "5\n"),
            new Configuration("/config/smth/file6", "6\n"),
            new Configuration("/config/smth/file7", "7\n")
        ), configurations);

        repository.deleteAll(List.of("/config/smth/file6"));
        configurationService.refreshConfiguration();
        Set<Configuration> updated = configurationService.findExternalConfiguration("smth");
        assertEquals(Set.of(
            new Configuration("/config/smth/file5", "5\n"),
            new Configuration("/config/smth/file7", "7\n")
        ), updated);

        Optional<Configuration> file3 = configurationService.findConfiguration("/config/file3");
        assertTrue(file3.isPresent());
        assertEquals("3\n", file3.get().getContent());

        Optional<Configuration> file4 = configurationService.findConfiguration("/config/dir/file4");
        assertTrue(file4.isPresent());
        assertEquals("4\n", file4.get().getContent());
    }

    @Test
    public void testExternalizationWhenOnlyTenantProfileUpdated() {
        memoryConfigStorage.clear();

        Configuration mainValue = mockConfig("XM", "${environment.variableFromFile}", "/some-config.yml");
        String tenantEnvValuePath = "/config/tenants/XM/tenant-profile.yml";
        Configuration tenantProfile = new Configuration(tenantEnvValuePath, TestUtil.loadFile("/tenant-profile.yml"));

        configurationService.updateConfigurationInMemory(List.of(mainValue));
        // update tenant profile after main value
        configurationService.updateConfigurationInMemory(List.of(tenantProfile));

        String content = mockConfig("XM", "VALUE_FROM_FILE", "/some-config.yml").getContent();
        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, List.of(mainValue.getPath()));
        assertEquals(content, privateMap.get(pathInTenant("XM", "/some-config.yml")).getContent());
    }

    @Test
    public void testExternalizationWithCrossReference() {
        memoryConfigStorage.clear();

        String tenantEnvValuePath = "/config/tenants/XM/tenant-profile.yml";
        String someConfigPath = "/config/tenants/XM/some-config.yml";
        Configuration tenantProfile = new Configuration(tenantEnvValuePath,
            // language=YAML
            """
            environment:
              resolvedVariable: VALUE_FROM_FILE
              resolvedVariable2: VALUE_FROM_FILE2
              crossReferencedVariable: ${environment.crossReferencedVariableWithValue}
              crossReferencedVariableWithValue: ${environment.resolvedVariable}
              crossReferencedValue2: ${environment.resolvedVariable2}
            """);
        Configuration mainValue = new Configuration(someConfigPath,
            // language=YAML
            """
            var1: ${environment.crossReferencedVariable}
            var2: ${environment.resolvedVariable2}
            """);

        configurationService.updateConfigurationInMemory(List.of(mainValue));
        // update tenant profile after main value
        configurationService.updateConfigurationInMemory(List.of(tenantProfile));

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, List.of(someConfigPath));
        assertEquals(
            // language=YAML
            """
            var1: VALUE_FROM_FILE
            var2: VALUE_FROM_FILE2
            """, privateMap.get(someConfigPath).getContent());
    }

    @Test
    public void testUpdateWhenExternalizationRemoved() {
        memoryConfigStorage.clear();

        Configuration mainValue = mockConfig("MAIN", "${environment.variableFromFile}", "/some-config.yml");
        String tenantEnvValuePath = "/config/tenants/MAIN/tenant-profile.yml";
        Configuration tenantProfile = new Configuration(tenantEnvValuePath, TestUtil.loadFile("/tenant-profile.yml"));

        configurationService.updateConfigurationInMemory(List.of(tenantProfile));
        configurationService.updateConfigurationInMemory(List.of(mainValue));

        String content = mockConfig("MAIN", "VALUE_FROM_FILE", "/some-config.yml").getContent();

        Map<String, Configuration> privateMap = configurationService.getConfigurationMap(null, filesList("/some-config.yml"));
        assertEquals(content, privateMap.get(pathInTenant("MAIN", "/some-config.yml")).getContent());

        Configuration configWithoutVariables = mockConfig("MAIN", "SOME_MOCK_VALUE", "/some-config.yml");
        configurationService.updateConfigurationInMemory(List.of(configWithoutVariables));
        Map<String, Configuration> newPrivateMap = configurationService.getConfigurationMap(null, filesList("/some-config.yml"));
        assertEquals(configWithoutVariables.getContent(), newPrivateMap.get(pathInTenant("MAIN", "/some-config.yml")).getContent());
    }

    private Map<String, Configuration> getPromPublicApi() {
        List<String> strings = filesList("/tenant-config.yml");
        List<Configuration> configList = strings.stream().map(configurationService::findConfiguration)
            .filter(Optional::isPresent).map(Optional::get).toList();
        return configList.stream().collect(toMap(Configuration::getPath, identity()));
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
        tenantAliasRefreshableConfiguration.onRefresh(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree.yml"));
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

    @Test
    public void testGetConfigMapAntPattern() {
        memoryConfigStorage.clear();

        Configuration config1 = mockConfig("XM", "mainSpecVal", "/xm.txt");
        Configuration config2 = mockConfig("SOMEXM", "submainSpecVal", "/some-xm.txt");

        configurationService.updateConfiguration(config1);
        configurationService.updateConfiguration(config2);

        Map<String, Configuration> allConfigs = Map.of(config1.getPath(), config1, config2.getPath(), config2);

        Map<String, Configuration> result1 = configurationService.getConfigMapAntPattern(
                null, List.of("/config/tenants/XM/*")
        );

        assertEquals(1, result1.size());
        assertEquals(config1, result1.get(config1.getPath()));



        Map<String, Configuration> result2 = configurationService.getConfigMapAntPattern(
                null, List.of("/config/tenants/SOMEXM/*")
        );

        assertEquals(1, result2.size());
        assertEquals(config2, result2.get(config2.getPath()));



        Map<String, Configuration> result3 = configurationService.getConfigMapAntPattern(
                null, List.of("/config/tenants/*/*.txt")
        );

        assertEquals(2, result3.size());
        assertEquals(allConfigs, result3);

        Map<String, Configuration> result4 = configurationService.getConfigMapAntPattern(
                null, List.of("/config/tenants/NONXISTS/*")
        );

        assertEquals(0, result4.size());
    }
}
