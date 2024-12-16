package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.service.TenantAliasTreeService.TENANT_ALIAS_CONFIG;
import static com.icthh.xm.ms.configuration.web.rest.TestUtil.loadFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.config.domain.TenantAliasTree;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantKey;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TenantAliasServiceUnitTest {

    TenantAliasTreeStorage tenantAliasTreeStorage;
    @Mock
    ConfigurationService configurationService;
    @Mock
    TenantContextHolder tenantContextHolder;
    TenantAliasTreeService tenantAliasService;

    @Before
    public void before() {
        tenantAliasTreeStorage = new TenantAliasTreeStorage(tenantContextHolder);
        tenantAliasService = new TenantAliasTreeService(configurationService, tenantAliasTreeStorage);
    }

    @Test
    public void testUpdateChangedTenantsDuringProcessConfiguration() {
        Configuration oldConfig = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree.yml"));
        tenantAliasService.updateAliasTree(oldConfig);

        verify(configurationService).refreshTenantsConfigurations(eq(List.of("ONEMORELIFETENANT", "SUBMAIN", "LIFETENANT")));

        verifyNoMoreInteractions(configurationService);

        reset(configurationService);

        Configuration newConfig = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree-updated.yml"));
        tenantAliasService.updateAliasTree(newConfig);

        verify(configurationService).refreshTenantsConfigurations(eq(List.of("MAINCHILDTENANT", "CHILDTENANT", "LIFETENANT")));
        verifyNoMoreInteractions(configurationService);
    }

    @Test
    @SneakyThrows
    public void testAddParent() {
        Configuration oldConfig = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree.yml"));
        tenantAliasService.updateAliasTree(oldConfig);

        verify(configurationService).refreshTenantsConfigurations(eq(List.of("ONEMORELIFETENANT", "SUBMAIN", "LIFETENANT")));

        verifyNoMoreInteractions(configurationService);

        reset(configurationService);

        TenantContext context = mock(TenantContext.class);
        when(context.getTenantKey()).thenReturn(Optional.ofNullable(TenantKey.valueOf("SUBMAIN")));
        when(tenantContextHolder.getContext()).thenReturn(context);

        tenantAliasService.setParent("ONEMORELIFETENANT");

        ArgumentCaptor<Configuration> argumentCaptor = ArgumentCaptor.forClass(Configuration.class);
        verify(configurationService).updateConfiguration(argumentCaptor.capture());
        Configuration configuration = argumentCaptor.getValue();

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        TenantAliasTree tenantAliasTree = mapper.readValue(configuration.getContent(), TenantAliasTree.class);
        TenantAliasTree tenantAliasTreeExpected = mapper.readValue(loadFile("tenantAliasTreeUpdatedParent.yml"), TenantAliasTree.class);

        assertThat(configuration.getPath()).isEqualTo(TENANT_ALIAS_CONFIG);
        assertThat(tenantAliasTree.getTenantAliasTree()).isEqualTo(tenantAliasTreeExpected.getTenantAliasTree());

        verifyNoMoreInteractions(configurationService);
    }

}
