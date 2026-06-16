package com.icthh.xm.ms.configuration.service.processors;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.AbstractUnitTest;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.web.rest.TestUtil;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SystemStubsExtension.class)
public class EnvConfigExternalizationUnitTest extends AbstractUnitTest {

    public static final Set<String> ENV_BLACKLIST = Set.of("filteredVariable", "filteredVariableWithDefault");

    @SystemStub
    private final EnvironmentVariables environmentVariables = new EnvironmentVariables();

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
