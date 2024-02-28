package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.lep.spring.LepService;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.config.lep.LepContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static org.junit.Assert.assertTrue;

@Slf4j
public class LepContextCastIntTest extends AbstractSpringBootTest {

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Autowired
    private XmAuthenticationContextHolder authContextHolder;

    @Autowired
    private LepManager lepManager;

    @Autowired
    private XmLepScriptConfigServerResourceLoader leps;

    @Autowired
    private TestLepService testLepService;

    @SneakyThrows
    @Before
    public void setup() {
        TenantContextUtils.setTenant(tenantContextHolder, "TEST_TENANT");

        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });
    }

    @After
    public void tearDown() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    @Test
    @SneakyThrows
    public void testLepContextCast() {
        String prefix = "/config/tenants/TEST_TENANT/configuration/lep/test/";
        String key = prefix + "ScriptWithAround$$around.groovy";
        String body = "import com.icthh.xm.ms.configuration.config.lep.LepContext;\nLepContext context = lepContext\nreturn ['context':context]";
        leps.onRefresh(key, body);
        Map<String, Object> result = testLepService.sayHello();
        log.info("result: {}", result);
        log.info("class: {}", result.get("context").getClass());
        assertTrue(result.get("context") instanceof LepContext);
        leps.onRefresh(key, null);
    }

    @Configuration
    public static class TestLepConfiguration {
        @Bean
        public TestLepService testLepService() {
            return new TestLepService();
        }
    }

    @LepService(group = "test")
    public static class TestLepService {
        @LogicExtensionPoint("ScriptWithAround")
        public Map<String, Object> sayHello() {
            return Map.of();
        }
    }


}
