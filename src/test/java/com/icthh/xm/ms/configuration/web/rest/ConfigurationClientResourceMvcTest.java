package com.icthh.xm.ms.configuration.web.rest;

import com.icthh.xm.commons.tenant.Tenant;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static com.icthh.xm.ms.configuration.config.Constants.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = ConfigurationClientResource.class)
@ContextConfiguration(classes = {ConfigurationClientResource.class})
public class ConfigurationClientResourceMvcTest {

    @MockBean
    private ConfigurationAdminResource configurationAdminResource;

    @MockBean
    private ConfigurationService configurationService;

    @MockBean
    private TenantContextHolder tenantContextHolder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @SneakyThrows
    public void ifPathPassedRefreshOnlyOneConfig() {
        TenantContext context = mock(TenantContext.class);
        when(context.getTenantKey()).thenReturn(Optional.ofNullable(TenantKey.valueOf("INTTEST")));
        when(tenantContextHolder.getContext()).thenReturn(context);

        String testPath = "/test/folder/subfolder/documentname";
        mockMvc.perform(post(API_PREFIX + PROFILE + REFRESH + testPath))
                .andExpect(status().is2xxSuccessful());

        verify(configurationService).refreshConfigurations(eq(CONFIG + TENANTS + "/INTTEST" + testPath));
        verifyNoMoreInteractions(configurationService);
    }

    @Test
    @SneakyThrows
    public void ifPathNotPassedRefreshAll() {
        TenantContext context = mock(TenantContext.class);
        when(context.getTenantKey()).thenReturn(Optional.ofNullable(TenantKey.valueOf("INTTEST")));
        when(tenantContextHolder.getContext()).thenReturn(context);


        mockMvc.perform(post(API_PREFIX + PROFILE + REFRESH))
                .andExpect(status().is2xxSuccessful());

        verify(configurationService).refreshTenantConfigurations();
        verifyNoMoreInteractions(configurationService);
    }

}
