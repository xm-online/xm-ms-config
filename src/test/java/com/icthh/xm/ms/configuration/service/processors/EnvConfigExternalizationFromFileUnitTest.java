package com.icthh.xm.ms.configuration.service.processors;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.web.rest.TestUtil;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvConfigExternalizationFromFileUnitTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    @SneakyThrows
    public void testConfigExternalizationFromFile() {
        environmentVariables.set("variable", "ENV_VALUE");
        environmentVariables.set("variableThatDefined", "ENV_DEF_VALUE");

        Configuration configuration = new Configuration("/config/tenants/XM/someConfig", TestUtil.loadFile("someConfig"));
        Map<String, Configuration> originalStorage = new HashMap<>();
        String tenantEnvValuePath = "/config/tenants/XM/tenant-profile.yml";
        Configuration originalStorageConfiguration = new Configuration(tenantEnvValuePath, TestUtil.loadFile("/tenant-profile.yml"));
        originalStorage.put(tenantEnvValuePath, originalStorageConfiguration);

        ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.setEnvConfigExternalizationEnabled(true);

        List<Configuration> processedConfigurations = new EnvConfigExternalizationFromFile(applicationProperties)
            .processConfiguration(configuration, originalStorage, emptyMap());
        assertEquals(TestUtil.loadFile("someConfigFromFileExpected"), processedConfigurations.get(0).getContent());
    }
}
