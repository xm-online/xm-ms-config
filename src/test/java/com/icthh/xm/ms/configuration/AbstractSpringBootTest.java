package com.icthh.xm.ms.configuration;

import com.icthh.xm.ms.configuration.config.LocalJGitRepositoryConfiguration;
import com.icthh.xm.ms.configuration.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.ms.configuration.config.TenantConfigMockConfiguration;
import com.icthh.xm.ms.configuration.config.TestLepConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(classes = {
    TestLepConfiguration.class,
    SecurityBeanOverrideConfiguration.class,
    TenantConfigMockConfiguration.class,
    ConfigurationApp.class,
    LocalJGitRepositoryConfiguration.class
})
@Tag("com.icthh.xm.ms.configuration.AbstractSpringBootTest")
@ExtendWith(SpringExtension.class)
public abstract class AbstractSpringBootTest {

    // TODO: To speedup test:
    //      - find all cases which break Spring context like @MockBean and fix.
    //      - separate tests by categories: Unit, SpringBoot, WebMwc

}
