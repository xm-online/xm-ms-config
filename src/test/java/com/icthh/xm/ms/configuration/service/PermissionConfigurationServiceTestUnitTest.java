package com.icthh.xm.ms.configuration.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.domain.TenantState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PermissionConfigurationServiceTestUnitTest {

    @InjectMocks
    PermissionConfigurationService permissionConfigurationService;

    @Mock
    private RestTemplate loadBalancedRestTemplate;
    @Mock
    private ConfigurationService configService;
    @Mock
    private TenantService tenantService;

    @Test
    public void testConfigurationUpdate() {
        //given
        when(tenantService.getTenants("uaa"))
            .thenReturn(ImmutableSet.of(
                new TenantState("test-inactive", "INACTIVE"),
                new TenantState("test-active-expected", "ACTIVE"),
                new TenantState("test-active-no-config", "ACTIVE"),
                new TenantState("test-active-disabled", "ACTIVE")
            ));

        when(configService.getConfigurationMap(any(),
            any()))
            .thenReturn(ImmutableMap.of("path",
                new Configuration("path", "uaa-permissions: true")))
            .thenReturn(ImmutableMap.of("path",
                new Configuration("path", "uaa-permissions: false")))
            .thenReturn(Collections.singletonMap("path", null));

        when(loadBalancedRestTemplate.exchange(
            ArgumentMatchers.contains("http://uaa/oauth/token?grant_type=client_credentials"),
            eq(HttpMethod.POST), any(HttpEntity.class), any(Class.class)
        )).thenReturn(new ResponseEntity<>(ImmutableMap.of(
            "token_type", "test-type",
            "access_token", "test-token"
        ), HttpStatus.OK));

        when(loadBalancedRestTemplate.exchange(
            ArgumentMatchers.contains("/roles/TEST-ACTIVE-EXPECTED/configuration"),
            eq(HttpMethod.GET), any(HttpEntity.class), any(Class.class)
        )).thenReturn(new ResponseEntity<>("test-roles-configuration", HttpStatus.OK));

        when(loadBalancedRestTemplate.exchange(
            ArgumentMatchers.contains("/permissions/TEST-ACTIVE-EXPECTED/configuration"),
            eq(HttpMethod.GET), any(HttpEntity.class), any(Class.class)
        )).thenReturn(new ResponseEntity<>("test-permissions-configuration", HttpStatus.OK));

        //when
        permissionConfigurationService.updateConfigurationFromUaa();

        //then
        verify(configService).updateConfigurationInMemory(new Configuration("/config/tenants/TEST-ACTIVE-EXPECTED/roles.yml", "test-roles-configuration"));
        verify(configService).updateConfigurationInMemory(new Configuration("/config/tenants/TEST-ACTIVE-EXPECTED/permissions.yml", "test-permissions-configuration"));
        verify(configService, times(3)).getConfigurationMap(any(), any());
        verifyNoMoreInteractions(configService);
    }
}
