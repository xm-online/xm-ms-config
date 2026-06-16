package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.config.TenantConfigMockConfiguration.failOnRefresh;

import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class StartUpRefreshIntTest extends AbstractSpringBootTest {

    @BeforeAll
    public static void init() {
        failOnRefresh.set(true);
    }

    @Test
    public void startUpRefreshTest() {
        // nothing here, just to check that context is loaded
    }

    @AfterAll
    public static void after() {
        failOnRefresh.set(false);
    }

}
