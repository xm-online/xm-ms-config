package com.icthh.xm.ms.configuration.service.processors;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.web.rest.TestUtil;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvConfigExternalizationUnitTest {

    public static final Set<String> ENV_BLACKLIST = Set.of("filteredVariable", "filteredVariableWithDefault");

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    @SneakyThrows
    public void testConfigExternalization() {
        environmentVariables.set("variable", "ENV_VALUE");
        environmentVariables.set("variableThatDefined", "ENV_DEF_VALUE");
        environmentVariables.set("filteredVariable", "ENV_FILTERED_VALUE_1");
        environmentVariables.set("filteredVariableWithDefault", "ENV_FILTERED_VALUE_2");

        ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.setEnvExternalizationBlacklist(ENV_BLACKLIST);

        Configuration configuration = new Configuration("/config/someConfig", TestUtil.loadFile("someConfig"));
        List<Configuration> processedConfigurations = new EnvConfigExternalizationFromFile(applicationProperties)
                .processConfiguration(configuration, emptyMap(), emptyMap(), new HashSet<>(), new HashMap<>());
        assertEquals(TestUtil.loadFile("someConfigExpected"), processedConfigurations.get(0).getContent());
    }

}
