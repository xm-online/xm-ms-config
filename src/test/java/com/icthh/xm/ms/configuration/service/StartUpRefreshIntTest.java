package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.config.TenantConfigMockConfiguration.failOnRefresh;

import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class StartUpRefreshIntTest extends AbstractSpringBootTest {

    @BeforeClass
    public static void init() {
        failOnRefresh.set(true);
    }

    @Test
    public void startUpRefreshTest() {
        // nothing here, just to check that context is loaded
    }

    @AfterClass
    public static void after() {
        failOnRefresh.set(false);
    }

}
