package com.icthh.xm.ms.configuration.security;

import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@TestPropertySource(properties = "application.adminApiRestrictionEnabled=true")
public class AdminApiAccessInterceptorIntTest extends AbstractSpringBootTest {

    private static final String ADMIN_API_DENIED = "Only privileges tenant can access admin api";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @SneakyThrows
    public void adminApiIsDeniedForNotSuperTenant() {
        assertEquals(ADMIN_API_DENIED, getErrorMessage("/api/config/tenants/XM/some-config.yml", "TESTX"));
        assertEquals(ADMIN_API_DENIED, getErrorMessage("/api/tenants/entity", "TESTX"));
    }

    @Test
    @SneakyThrows
    public void inMemoryApiIsNotRestrictedToSuperTenants() {
        assertNotEquals(ADMIN_API_DENIED, putErrorMessage("/api/inmemory/config/tenants/TESTX/some-config.yml", "TESTX"));
    }

    @Test
    @SneakyThrows
    public void adminApiIsNotDeniedForSuperTenant() {
        assertNotEquals(ADMIN_API_DENIED, getErrorMessage("/api/config/tenants/XM/some-config.yml", "XM"));
        assertNotEquals(ADMIN_API_DENIED, getErrorMessage("/api/tenants/entity", "MANAGER"));
    }

    @SneakyThrows
    private String getErrorMessage(String path, String tenant) {
        return mockMvc.perform(get(path).header("x-tenant", tenant)).andReturn().getResponse().getErrorMessage();
    }

    @SneakyThrows
    private String putErrorMessage(String path, String tenant) {
        return mockMvc.perform(put(path).header("x-tenant", tenant).contentType(TEXT_PLAIN).content("key: value"))
            .andReturn().getResponse().getErrorMessage();
    }
}
