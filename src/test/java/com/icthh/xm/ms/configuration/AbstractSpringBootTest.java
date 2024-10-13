package com.icthh.xm.ms.configuration;

import com.icthh.xm.ms.configuration.config.LocalJGitRepositoryConfiguration;
import com.icthh.xm.ms.configuration.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.ms.configuration.config.TenantConfigMockConfiguration;
import com.icthh.xm.ms.configuration.config.TestLepConfiguration;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = {
    TestLepConfiguration.class,
    TenantConfigMockConfiguration.class,
    ConfigurationApp.class,
    SecurityBeanOverrideConfiguration.class,
    LocalJGitRepositoryConfiguration.class
})
@RunWith(SpringRunner.class)
public abstract class AbstractSpringBootTest {

    // TODO: To speedup test:
    //      - find all cases which break Spring context like @MockBean and fix.
    //      - separate tests by categories: Unit, SpringBoot, WebMwc

}
