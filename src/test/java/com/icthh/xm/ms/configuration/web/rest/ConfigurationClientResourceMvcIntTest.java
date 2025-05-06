package com.icthh.xm.ms.configuration.web.rest;

import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.PROFILE;
import static com.icthh.xm.ms.configuration.config.Constants.REFRESH;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConfigurationClientResourceMvcIntTest extends AbstractSpringBootTest {

    @MockBean
    private ConfigurationAdminResource configurationAdminResource;

    @MockBean
    private ConfigurationService configurationService;

    @Mock
    private TenantContextHolder tenantContextHolder;

    private MockMvc restTaskMockMvc;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        this.restTaskMockMvc = MockMvcBuilders.standaloneSetup(new ConfigurationClientResource(configurationAdminResource, configurationService, tenantContextHolder))
            .build();
    }

    @Test
    @SneakyThrows
    public void ifPathPassedRefreshOnlyOneConfig() {
        TenantContext context = mock(TenantContext.class);
        when(context.getTenantKey()).thenReturn(Optional.ofNullable(TenantKey.valueOf("INTTEST")));
        when(tenantContextHolder.getContext()).thenReturn(context);

        String testPath = "/test/folder/subfolder/documentname";
        restTaskMockMvc.perform(post(API_PREFIX + PROFILE + REFRESH + testPath))
            .andExpect(status().is2xxSuccessful());

        verify(configurationService).refreshConfiguration(eq(CONFIG + TENANTS + "/INTTEST" + testPath));
        verifyNoMoreInteractions(configurationService);
    }

    @Test
    @SneakyThrows
    public void ifPathNotPassedRefreshAll() {
        TenantContext context = mock(TenantContext.class);
        when(context.getTenantKey()).thenReturn(Optional.ofNullable(TenantKey.valueOf("INTTEST")));
        when(tenantContextHolder.getContext()).thenReturn(context);


        restTaskMockMvc.perform(post(API_PREFIX + PROFILE + REFRESH))
            .andExpect(status().is2xxSuccessful());

        verify(configurationService).refreshTenantConfigurations();
        verifyNoMoreInteractions(configurationService);
    }

}
