package com.icthh.xm.ms.configuration.service.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IncludeConfigurationProcessorIntTest extends AbstractSpringBootTest {

    @MockBean
    ConfigTopicProducer configTopicProducer;

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    MemoryConfigStorage memoryConfigStorage;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Before
    public void before() {
        memoryConfigStorage.clear();
    }

    @SneakyThrows
    private Map<String, Object> parseJson(String content) {
        return jsonMapper.readValue(content, Map.class);
    }

    @SneakyThrows
    private Map<String, Object> parseYaml(String content) {
        return yamlMapper.readValue(content, Map.class);
    }

    @Test
    @SneakyThrows
    public void testIncludeJsonFile() {
        // Create the first file to be included
        String includedFilePath = "/config/tenants/XM/common/database.json";
        String includedFileContent = """
            {
              "host": "localhost",
              "port": 5432,
              "database": "testdb"
            }
            """;

        // Create the second file that includes the first one
        String mainFilePath = "/config/tenants/XM/config.json";
        String mainFileContent = """
            {
              "$include": "common/database.json",
              "appName": "myapp"
            }
            """;

        // Expected result after include processing
        String expectedContent = """
            {
              "host": "localhost",
              "port": 5432,
              "database": "testdb",
              "appName": "myapp"
            }
            """;

        // Post both files
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration using getConfigurationMap
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeYamlFile() {
        // Create the first file to be included
        String includedFilePath = "/config/tenants/XM/common/settings.yml";
        String includedFileContent = """
            timeout: 30
            retries: 3
            enabled: true
            """;

        // Create the second file that includes the first one
        String mainFilePath = "/config/tenants/XM/application.yml";
        String mainFileContent = """
            $include: common/settings.yml
            appName: myapp
            version: 1.0.0
            """;

        // Expected result after include processing
        String expectedContent = """
            timeout: 30
            retries: 3
            enabled: true
            appName: myapp
            version: 1.0.0
            """;

        // Post both files
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration using getConfigurationMap
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseYaml(processedConfig.getContent());
        Map<String, Object> expectedMap = parseYaml(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeAbsolutePath() {
        // Create the first file to be included
        String includedFilePath = "/config/tenants/XM/shared/credentials.json";
        String includedFileContent = """
            {
              "username": "admin",
              "password": "secret"
            }
            """;

        // Create the second file that includes using absolute path
        String mainFilePath = "/config/tenants/XM/app/config.json";
        String mainFileContent = """
            {
              "$include": "/config/tenants/XM/shared/credentials.json",
              "apiUrl": "http://localhost:8080"
            }
            """;

        // Expected result after include processing
        String expectedContent = """
            {
              "username": "admin",
              "password": "secret",
              "apiUrl": "http://localhost:8080"
            }
            """;

        // Post both files
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration using getConfigurationMap
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeMissingFile() {
        // Create a file that includes a non-existent file
        String mainFilePath = "/config/tenants/XM/config.json";
        String mainFileContent = """
            {
              "$include": "nonexistent.json",
              "appName": "myapp"
            }
            """;

        // Post the file
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration using getConfigurationMap
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        String processedContent = processedConfig.getContent();

        // Verify that the $include is left unchanged when file is not found
        assertTrue("Should contain $include", processedContent.contains("$include"));
        assertTrue("Should contain appName", processedContent.contains("appName"));
    }

    @Test
    @SneakyThrows
    public void testNestedInclude() {
        // Create the base file
        String baseFilePath = "/config/tenants/XM/base.json";
        String baseFileContent = """
            {
              "level": "base"
            }
            """;

        // Create the middle file that includes base
        String middleFilePath = "/config/tenants/XM/middle.json";
        String middleFileContent = """
            {
              "$include": "base.json",
              "middleLevel": "middle"
            }
            """;

        // Create the top file that includes middle
        String topFilePath = "/config/tenants/XM/top.json";
        String topFileContent = """
            {
              "$include": "middle.json",
              "topLevel": "top"
            }
            """;

        // Expected result after include processing
        String expectedContent = """
            {
              "level": "base",
              "middleLevel": "middle",
              "topLevel": "top"
            }
            """;

        // Post all files
        configurationService.updateConfiguration(new Configuration(baseFilePath, baseFileContent));
        configurationService.updateConfiguration(new Configuration(middleFilePath, middleFileContent));
        configurationService.updateConfiguration(new Configuration(topFilePath, topFileContent));

        // Retrieve the processed configuration using getConfigurationMap
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(topFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(topFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testDependencyReprocessing() {
        // Create the included file
        String includedFilePath = "/config/tenants/XM/shared/data.json";
        String includedFileContent = """
            {
              "value": "initial"
            }
            """;

        // Create the file that includes it
        String mainFilePath = "/config/tenants/XM/config.json";
        String mainFileContent = """
            {
              "$include": "shared/data.json",
              "appName": "myapp"
            }
            """;

        // Expected initial result
        String expectedInitialContent = """
            {
              "value": "initial",
              "appName": "myapp"
            }
            """;

        // Post both files
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Verify initial state
        Map<String, Configuration> initialConfigMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));
        Configuration initialConfig = initialConfigMap.get(mainFilePath);
        assertNotNull("Configuration should be present", initialConfig);

        Map<String, Object> actualInitialMap = parseJson(initialConfig.getContent());
        Map<String, Object> expectedInitialMap = parseJson(expectedInitialContent);
        assertEquals("Initial configuration should match expected", expectedInitialMap, actualInitialMap);

        // Update the included file
        String updatedIncludedContent = """
            {
              "value": "updated"
            }
            """;

        // Expected updated result
        String expectedUpdatedContent = """
            {
              "value": "updated",
              "appName": "myapp"
            }
            """;

        configurationService.updateConfiguration(new Configuration(includedFilePath, updatedIncludedContent));

        // Verify that the main file is reprocessed
        Map<String, Configuration> updatedConfigMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));
        Configuration updatedConfig = updatedConfigMap.get(mainFilePath);
        assertNotNull("Configuration should be present", updatedConfig);

        Map<String, Object> actualUpdatedMap = parseJson(updatedConfig.getContent());
        Map<String, Object> expectedUpdatedMap = parseJson(expectedUpdatedContent);
        assertEquals("Updated configuration should match expected", expectedUpdatedMap, actualUpdatedMap);
    }

    @Test
    @SneakyThrows
    public void testNoIncludeKeyword() {
        // Create a file without $include keyword
        String filePath = "/config/tenants/XM/simple.json";
        String fileContent = """
            {
              "name": "test",
              "value": 123
            }
            """;

        // Post the file
        configurationService.updateConfiguration(new Configuration(filePath, fileContent));

        // Retrieve the configuration using getConfigurationMap
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(filePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration config = configMap.get(filePath);
        assertNotNull("Configuration should be present", config);

        // Parse and compare as maps - content should be unchanged
        Map<String, Object> actualMap = parseJson(config.getContent());
        Map<String, Object> expectedMap = parseJson(fileContent);

        assertEquals("Configuration should remain unchanged", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeWithDotSlashPath() {
        // Create the included file
        String includedFilePath = "/config/tenants/XM/app/common.json";
        String includedFileContent = """
            {
              "timezone": "UTC",
              "locale": "en_US"
            }
            """;

        // Create the main file using ./relative path
        String mainFilePath = "/config/tenants/XM/app/config.json";
        String mainFileContent = """
            {
              "$include": "./common.json",
              "appName": "myapp"
            }
            """;

        // Expected result after include processing
        String expectedContent = """
            {
              "timezone": "UTC",
              "locale": "en_US",
              "appName": "myapp"
            }
            """;

        // Post both files
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeWithParentDirectoryPath() {
        // Create the included file in parent directory
        String includedFilePath = "/config/tenants/XM/shared.json";
        String includedFileContent = """
            {
              "sharedKey": "sharedValue",
              "env": "production"
            }
            """;

        // Create the main file in subdirectory using ../ to go up
        String mainFilePath = "/config/tenants/XM/app/services/config.json";
        String mainFileContent = """
            {
              "$include": "../../shared.json",
              "serviceName": "api"
            }
            """;

        // Expected result after include processing
        String expectedContent = """
            {
              "sharedKey": "sharedValue",
              "env": "production",
              "serviceName": "api"
            }
            """;

        // Post both files
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeWithMultipleParentDirectories() {
        // Create the included file deep in the structure
        String includedFilePath = "/config/tenants/XM/base/config.json";
        String includedFileContent = """
            {
              "baseUrl": "https://api.example.com",
              "apiVersion": "v1"
            }
            """;

        // Create the main file in a deeply nested directory using ../../../ to navigate up
        String mainFilePath = "/config/tenants/XM/app/module/submodule/service/config.json";
        String mainFileContent = """
            {
              "$include": "../../../../base/config.json",
              "moduleConfig": "moduleValue"
            }
            """;

        // Expected result after include processing
        String expectedContent = """
            {
              "baseUrl": "https://api.example.com",
              "apiVersion": "v1",
              "moduleConfig": "moduleValue"
            }
            """;

        // Post both files
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeInNestedObject() {
        // Create the included file
        String includedFilePath = "/config/tenants/XM/db-credentials.json";
        String includedFileContent = """
            {
              "username": "dbuser",
              "password": "dbpass"
            }
            """;

        // Create the main file with $include in nested object
        String mainFilePath = "/config/tenants/XM/config.json";
        String mainFileContent = """
            {
              "appName": "myapp",
              "database": {
                "host": "localhost",
                "port": 5432,
                "credentials": {
                  "$include": "db-credentials.json"
                }
              }
            }
            """;

        // Expected result after include processing
        String expectedContent = """
            {
              "appName": "myapp",
              "database": {
                "host": "localhost",
                "port": 5432,
                "credentials": {
                  "username": "dbuser",
                  "password": "dbpass"
                }
              }
            }
            """;

        // Post both files
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeInArray() {
        // Create the included files for array elements
        String includedFile1Path = "/config/tenants/XM/service1.json";
        String includedFile1Content = """
            {
              "name": "auth-service",
              "port": 8081
            }
            """;

        String includedFile2Path = "/config/tenants/XM/service2.json";
        String includedFile2Content = """
            {
              "name": "user-service",
              "port": 8082
            }
            """;

        // Create the main file with $include in array
        String mainFilePath = "/config/tenants/XM/services.json";
        String mainFileContent = """
            {
              "appName": "microservices",
              "services": [
                {
                  "$include": "service1.json"
                },
                {
                  "$include": "service2.json"
                },
                {
                  "name": "direct-service",
                  "port": 8083
                }
              ]
            }
            """;

        // Expected result after include processing
        String expectedContent = """
            {
              "appName": "microservices",
              "services": [
                {
                  "name": "auth-service",
                  "port": 8081
                },
                {
                  "name": "user-service",
                  "port": 8082
                },
                {
                  "name": "direct-service",
                  "port": 8083
                }
              ]
            }
            """;

        // Post all files
        configurationService.updateConfiguration(new Configuration(includedFile1Path, includedFile1Content));
        configurationService.updateConfiguration(new Configuration(includedFile2Path, includedFile2Content));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeYamlInNestedStructure() {
        // Create the included YAML file
        String includedFilePath = "/config/tenants/XM/logging-config.yml";
        String includedFileContent = """
            level: INFO
            format: json
            output: stdout
            """;

        // Create the main YAML file with nested include
        String mainFilePath = "/config/tenants/XM/app-config.yml";
        String mainFileContent = """
            application:
              name: my-service
              logging:
                $include: logging-config.yml
            server:
              port: 9090
            """;

        // Expected result after include processing
        String expectedContent = """
            application:
              name: my-service
              logging:
                level: INFO
                format: json
                output: stdout
            server:
              port: 9090
            """;

        // Post both files
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseYaml(processedConfig.getContent());
        Map<String, Object> expectedMap = parseYaml(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeFileWithDollarIncludeTextAndMainFileHasEnvironmentVariable() {
        // Create tenant profile with environment variable
        String tenantProfilePath = "/config/tenants/XM/tenant-profile.yml";
        String tenantProfileContent = """
            environment:
              apiToken: SECRET_TOKEN_123
            """;

        // Create the included file that contains "$include" as text (not a directive)
        String includedFilePath = "/config/tenants/XM/help.json";
        String includedFileContent = """
            {
              "helpText": "To include other configs, use $include key",
              "example": "$include: common.json"
            }
            """;

        // Create the main file that includes the help file AND has an environment variable
        String mainFilePath = "/config/tenants/XM/app-config.json";
        String mainFileContent = """
            {
              "$include": "help.json",
              "apiKey": "${environment.apiToken}",
              "appName": "myapp"
            }
            """;

        // Expected result: included content merged, $include text preserved, env variable replaced
        String expectedContent = """
            {
              "helpText": "To include other configs, use $include key",
              "example": "$include: common.json",
              "apiKey": "SECRET_TOKEN_123",
              "appName": "myapp"
            }
            """;

        // Post tenant profile first, then the config files
        configurationService.updateConfiguration(new Configuration(tenantProfilePath, tenantProfileContent));
        configurationService.updateConfiguration(new Configuration(includedFilePath, includedFileContent));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps for exact match
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeArrayJsonFiles() {
        // Create multiple files to be included
        String includedFile1Path = "/config/tenants/XM/common/database.json";
        String includedFile1Content = """
            {
              "dbHost": "localhost",
              "dbPort": 5432
            }
            """;

        String includedFile2Path = "/config/tenants/XM/common/cache.json";
        String includedFile2Content = """
            {
              "cacheEnabled": true,
              "cacheTtl": 3600
            }
            """;

        String includedFile3Path = "/config/tenants/XM/common/logging.json";
        String includedFile3Content = """
            {
              "logLevel": "INFO",
              "logFormat": "json"
            }
            """;

        // Create the main file that includes all three files using array syntax
        String mainFilePath = "/config/tenants/XM/config.json";
        String mainFileContent = """
            {
              "$include": ["common/database.json", "common/cache.json", "common/logging.json"],
              "appName": "myapp"
            }
            """;

        // Expected result after include processing - all files merged
        String expectedContent = """
            {
              "dbHost": "localhost",
              "dbPort": 5432,
              "cacheEnabled": true,
              "cacheTtl": 3600,
              "logLevel": "INFO",
              "logFormat": "json",
              "appName": "myapp"
            }
            """;

        // Post all files
        configurationService.updateConfiguration(new Configuration(includedFile1Path, includedFile1Content));
        configurationService.updateConfiguration(new Configuration(includedFile2Path, includedFile2Content));
        configurationService.updateConfiguration(new Configuration(includedFile3Path, includedFile3Content));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeArrayYamlFiles() {
        // Create multiple YAML files to be included
        String includedFile1Path = "/config/tenants/XM/common/database.yml";
        String includedFile1Content = """
            dbHost: localhost
            dbPort: 5432
            """;

        String includedFile2Path = "/config/tenants/XM/common/cache.yml";
        String includedFile2Content = """
            cacheEnabled: true
            cacheTtl: 3600
            """;

        // Create the main file that includes both files using array syntax
        String mainFilePath = "/config/tenants/XM/config.yml";
        String mainFileContent = """
            $include:
              - common/database.yml
              - common/cache.yml
            appName: myapp
            """;

        // Expected result after include processing
        String expectedContent = """
            dbHost: localhost
            dbPort: 5432
            cacheEnabled: true
            cacheTtl: 3600
            appName: myapp
            """;

        // Post all files
        configurationService.updateConfiguration(new Configuration(includedFile1Path, includedFile1Content));
        configurationService.updateConfiguration(new Configuration(includedFile2Path, includedFile2Content));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseYaml(processedConfig.getContent());
        Map<String, Object> expectedMap = parseYaml(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeArrayWithMissingFile() {
        // Create only one of the files to be included
        String includedFile1Path = "/config/tenants/XM/common/existing.json";
        String includedFile1Content = """
            {
              "existingKey": "existingValue"
            }
            """;

        // Create the main file that includes both existing and non-existing files
        String mainFilePath = "/config/tenants/XM/config.json";
        String mainFileContent = """
            {
              "$include": ["common/existing.json", "common/nonexistent1.json", "common/nonexistent2.json"],
              "appName": "myapp"
            }
            """;

        // Post files
        configurationService.updateConfiguration(new Configuration(includedFile1Path, includedFile1Content));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse result
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());

        // The existing file should be merged
        assertEquals("existingValue", actualMap.get("existingKey"));
        assertEquals("myapp", actualMap.get("appName"));
        // The non-existing file path should remain in $include
        assertEquals(List.of("common/nonexistent1.json", "common/nonexistent2.json"), actualMap.get("$include"));
    }

    @Test
    @SneakyThrows
    public void testIncludeArrayInNestedObject() {
        // Create files to be included
        String includedFile1Path = "/config/tenants/XM/db-credentials.json";
        String includedFile1Content = """
            {
              "username": "dbuser",
              "password": "dbpass"
            }
            """;

        String includedFile2Path = "/config/tenants/XM/db-settings.json";
        String includedFile2Content = """
            {
              "poolSize": 10,
              "timeout": 30
            }
            """;

        // Create the main file with $include array in nested object
        String mainFilePath = "/config/tenants/XM/config.json";
        String mainFileContent = """
            {
              "appName": "myapp",
              "database": {
                "host": "localhost",
                "port": 5432,
                "$include": ["db-credentials.json", "db-settings.json"]
              }
            }
            """;

        // Expected result after include processing
        String expectedContent = """
            {
              "appName": "myapp",
              "database": {
                "host": "localhost",
                "port": 5432,
                "username": "dbuser",
                "password": "dbpass",
                "poolSize": 10,
                "timeout": 30
              }
            }
            """;

        // Post all files
        configurationService.updateConfiguration(new Configuration(includedFile1Path, includedFile1Content));
        configurationService.updateConfiguration(new Configuration(includedFile2Path, includedFile2Content));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeArrayWithAbsoluteAndRelativePaths() {
        // Create files to be included
        String includedFile1Path = "/config/tenants/XM/shared/global.json";
        String includedFile1Content = """
            {
              "globalKey": "globalValue"
            }
            """;

        String includedFile2Path = "/config/tenants/XM/app/local.json";
        String includedFile2Content = """
            {
              "localKey": "localValue"
            }
            """;

        // Create the main file with mixed absolute and relative paths
        String mainFilePath = "/config/tenants/XM/app/config.json";
        String mainFileContent = """
            {
              "$include": ["/config/tenants/XM/shared/global.json", "local.json"],
              "appName": "myapp"
            }
            """;

        // Expected result
        String expectedContent = """
            {
              "globalKey": "globalValue",
              "localKey": "localValue",
              "appName": "myapp"
            }
            """;

        // Post all files
        configurationService.updateConfiguration(new Configuration(includedFile1Path, includedFile1Content));
        configurationService.updateConfiguration(new Configuration(includedFile2Path, includedFile2Content));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Retrieve the processed configuration
        Map<String, Configuration> configMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));

        assertNotNull("Configuration map should not be null", configMap);
        Configuration processedConfig = configMap.get(mainFilePath);
        assertNotNull("Configuration should be present", processedConfig);

        // Parse and compare as maps
        Map<String, Object> actualMap = parseJson(processedConfig.getContent());
        Map<String, Object> expectedMap = parseJson(expectedContent);

        assertEquals("Processed configuration should match expected", expectedMap, actualMap);
    }

    @Test
    @SneakyThrows
    public void testIncludeArrayDependencyReprocessing() {
        // Create the included files
        String includedFile1Path = "/config/tenants/XM/shared/data1.json";
        String includedFile1Content = """
            {
              "value1": "initial1"
            }
            """;

        String includedFile2Path = "/config/tenants/XM/shared/data2.json";
        String includedFile2Content = """
            {
              "value2": "initial2"
            }
            """;

        // Create the file that includes both
        String mainFilePath = "/config/tenants/XM/config.json";
        String mainFileContent = """
            {
              "$include": ["shared/data1.json", "shared/data2.json"],
              "appName": "myapp"
            }
            """;

        // Post all files
        configurationService.updateConfiguration(new Configuration(includedFile1Path, includedFile1Content));
        configurationService.updateConfiguration(new Configuration(includedFile2Path, includedFile2Content));
        configurationService.updateConfiguration(new Configuration(mainFilePath, mainFileContent));

        // Verify initial state
        Map<String, Configuration> initialConfigMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));
        Configuration initialConfig = initialConfigMap.get(mainFilePath);
        Map<String, Object> actualInitialMap = parseJson(initialConfig.getContent());

        assertEquals("initial1", actualInitialMap.get("value1"));
        assertEquals("initial2", actualInitialMap.get("value2"));

        // Update one of the included files
        String updatedIncludedContent = """
            {
              "value1": "updated1"
            }
            """;

        configurationService.updateConfiguration(new Configuration(includedFile1Path, updatedIncludedContent));

        // Verify that the main file is reprocessed with updated value
        Map<String, Configuration> updatedConfigMap = configurationService.getConfigurationMap(null, List.of(mainFilePath));
        Configuration updatedConfig = updatedConfigMap.get(mainFilePath);
        Map<String, Object> actualUpdatedMap = parseJson(updatedConfig.getContent());

        assertEquals("updated1", actualUpdatedMap.get("value1"));
        assertEquals("initial2", actualUpdatedMap.get("value2"));
        assertEquals("myapp", actualUpdatedMap.get("appName"));
    }
}
