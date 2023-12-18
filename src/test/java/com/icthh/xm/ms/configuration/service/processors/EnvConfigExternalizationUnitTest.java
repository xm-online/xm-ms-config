package com.icthh.xm.ms.configuration.service.processors;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.web.rest.TestUtil;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.List;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnvConfigExternalizationUnitTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    @SneakyThrows
    public void testConfigExternalization() {
        environmentVariables.set("variable", "ENV_VALUE");
        environmentVariables.set("variableThatDefined", "ENV_DEF_VALUE");

        Configuration configuration = new Configuration("/config/someConfig", TestUtil.loadFile("someConfig"));
        List<Configuration> processedConfigurations = new EnvConfigExternalizationFromFile()
                .processConfiguration(configuration, emptyMap(), emptyMap());
        assertEquals(TestUtil.loadFile("someConfigExpected"), processedConfigurations.get(0).getContent());
    }

}
