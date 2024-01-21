package com.icthh.xm.ms.configuration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.domain.TenantAliasTree;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.icthh.xm.ms.configuration.service.TenantAliasService.TENANT_ALIAS_CONFIG;
import static com.icthh.xm.ms.configuration.web.rest.TestUtil.loadFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TenantAliasServiceUnitTest {

    @Mock
    ConfigurationService configurationService;
    @Mock
    MemoryConfigStorage memoryConfigStorage;
    @Mock
    TenantContextHolder tenantContextHolder;
    @InjectMocks
    TenantAliasService tenantAliasService;

    @Test
    public void testUpdateChangedTenantsDuringProcessConfiguration() {
        Configuration oldConfig = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree.yml"));
        tenantAliasService.processConfiguration(oldConfig, Map.of(), Map.of(), Set.of());

        verify(memoryConfigStorage).reprocess(eq("MAIN"));
        verify(memoryConfigStorage).reprocess(eq("SUBMAIN"));
        verify(configurationService).refreshTenantConfigurations(eq("SUBMAIN"));
        verify(configurationService).refreshTenantConfigurations(eq("LIFETENANT"));
        verify(configurationService).refreshTenantConfigurations(eq("ONEMORELIFETENANT"));

        verifyNoMoreInteractions(memoryConfigStorage);
        verifyNoMoreInteractions(configurationService);

        reset(memoryConfigStorage);
        reset(configurationService);

        Configuration newConfig = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree-updated.yml"));
        tenantAliasService.processConfiguration(newConfig, Map.of(), Map.of(), Set.of());

        verify(memoryConfigStorage).reprocess(eq("MAIN"));
        verify(memoryConfigStorage).reprocess(eq("ONEMORELIFETENANT"));
        verify(memoryConfigStorage).reprocess(eq("NEWPARENTTENANTSECOND"));
        verify(configurationService).refreshTenantConfigurations(eq("MAINCHILDTENANT"));
        verify(configurationService).refreshTenantConfigurations(eq("CHILDTENANT"));
        verify(configurationService).refreshTenantConfigurations(eq("LIFETENANT"));

        verifyNoMoreInteractions(memoryConfigStorage);
        verifyNoMoreInteractions(configurationService);

    }

    @Test
    @SneakyThrows
    public void testAddParent() {
        Configuration oldConfig = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree.yml"));
        tenantAliasService.processConfiguration(oldConfig, Map.of(), Map.of(), Set.of());

        verify(memoryConfigStorage).reprocess(eq("MAIN"));
        verify(memoryConfigStorage).reprocess(eq("SUBMAIN"));
        verify(configurationService).refreshTenantConfigurations(eq("SUBMAIN"));
        verify(configurationService).refreshTenantConfigurations(eq("LIFETENANT"));
        verify(configurationService).refreshTenantConfigurations(eq("ONEMORELIFETENANT"));

        verifyNoMoreInteractions(memoryConfigStorage);
        verifyNoMoreInteractions(configurationService);

        reset(memoryConfigStorage);
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

        verifyNoMoreInteractions(memoryConfigStorage);
        verifyNoMoreInteractions(configurationService);
    }

}
