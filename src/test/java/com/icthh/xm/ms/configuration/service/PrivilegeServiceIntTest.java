package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.permission.config.PermissionProperties;
import com.icthh.xm.commons.permission.domain.Privilege;
import com.icthh.xm.commons.request.XmRequestContext;
import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.AbstractSpringBootTest;
import com.icthh.xm.ms.configuration.config.RequestContextKeys;
import com.icthh.xm.ms.configuration.domain.RequestSourceType;
import com.icthh.xm.ms.configuration.domain.TenantState;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static com.icthh.xm.ms.configuration.domain.RequestSourceType.SYSTEM_QUEUE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PrivilegeServiceIntTest extends AbstractSpringBootTest {

    @InjectMocks
    private PrivilegeService privilegeService;

    @Mock
    private PermissionProperties properties;
    @Mock
    private ConfigurationService configurationService;
    @Mock
    private TenantService tenantService;
    @Mock
    private TenantContext tenantContext;
    @Mock
    private XmRequestContext xmRequestContext;
    @Mock
    private XmRequestContextHolder requestContextHolder;

    @Before
    public void before() {
        when(tenantContext.getTenantKey()).thenReturn(Optional.of(TenantKey.valueOf("tenant")));
        when(xmRequestContext.getValue(RequestContextKeys.REQUEST_SOURCE_TYPE,
            RequestSourceType.class)).thenReturn(SYSTEM_QUEUE);
        when(requestContextHolder.getContext()).thenReturn(xmRequestContext);
    }

    @Test
    public void testUpdatePrivileges_shouldHandleScenarioWhenTwoAppsHaveSamePrivilegeName() throws IOException {
        doReturn(Set.of(new TenantState("tenant", "state")))
            .when(tenantService).getTenants("paymentcustomer");

        doReturn("permissions.yml").when(properties).getPermissionsSpecPath();
        doReturn("privileges.yml").when(properties).getPrivilegesSpecPath();

        doReturn(Optional.of(getConfiguration("privileges/privileges_initial.yml")))
            .when(configurationService)
            .findConfiguration("privileges.yml");
        doReturn(Optional.of(getConfiguration("permissions/permissions_initial.yml")))
            .when(configurationService)
            .findConfiguration("permissions.yml");

        HashSet<Privilege> privileges = new HashSet<>();
        Privilege privilege = new Privilege();
        privilege.setKey("CUSTOMER.BLACK_LIST_RECORD.CREATE");
        privilege.setMsName("paymentcustomer");
        privileges.add(privilege);

        // test
        privilegeService.updatePrivileges("paymentcustomer", privileges);

        // verify
        ArgumentCaptor<Configuration> argument = ArgumentCaptor.forClass(Configuration.class);
        verify(configurationService, times(1)).updateConfiguration(argument.capture());

        List<Configuration> allCaptures = argument.getAllValues();
        Configuration privilegeUpdate = allCaptures.get(0);
        String expectedPrivileges = readFile("privileges/privileges_expected_update.yml");

        assertEquals("privileges.yml", privilegeUpdate.getPath());
        assertThat(privilegeUpdate.getContent()).isEqualToNormalizingNewlines(expectedPrivileges);
    }

    private Configuration getConfiguration(String path) throws IOException {
        String content = readFile(path);
        Configuration config = new Configuration();
        config.setContent(content);
        return config;
    }

    private String readFile(String path) throws IOException {
        InputStream cfgInputStream = new ClassPathResource(path).getInputStream();
        return IOUtils.toString(cfgInputStream, StandardCharsets.UTF_8);
    }
}
