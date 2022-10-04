package com.icthh.xm.ms.configuration.service;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.ms.configuration.repository.impl.MemoryConfigStorage;
import java.util.Map;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class TenantAliasServiceUnitTest {

    @Mock
    ConfigurationService configurationService;
    @Mock
    MemoryConfigStorage memoryConfigStorage;
    @InjectMocks
    TenantAliasService tenantAliasService;

    @Test
    public void testUpdateChangedTenantsDuringProcessConfiguration() {
        Configuration oldConfig = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTree.yml"));
        tenantAliasService.processConfiguration(oldConfig, Map.of(), Map.of());

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
        tenantAliasService.processConfiguration(newConfig, Map.of(), Map.of());

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
    public void testAddParent() {
        Configuration oldConfig = new Configuration(TENANT_ALIAS_CONFIG, loadFile("tenantAliasTreeToUpdateParent.yml"));
        tenantAliasService.processConfiguration(oldConfig, Map.of(), Map.of());

        verify(memoryConfigStorage).reprocess(eq("MAIN"));
        verify(memoryConfigStorage).reprocess(eq("SUBMAIN"));
        verify(configurationService).refreshTenantConfigurations(eq("SUBMAIN"));
        verify(configurationService).refreshTenantConfigurations(eq("LIFETENANT"));
        verify(configurationService).refreshTenantConfigurations(eq("ONEMORELIFETENANT"));

        verifyNoMoreInteractions(memoryConfigStorage);
        verifyNoMoreInteractions(configurationService);

        reset(memoryConfigStorage);
        reset(configurationService);

        tenantAliasService.addParent("NEWPARENTTENANT", "SUBMAIN");

        ArgumentCaptor<Configuration> argumentCaptor = ArgumentCaptor.forClass(Configuration.class);
        verify(configurationService).updateConfiguration(argumentCaptor.capture());
        Configuration configuration = argumentCaptor.getValue();
       // System.out.print(configuration.getContent());
        assertThat(configuration.getPath()).isEqualTo(TENANT_ALIAS_CONFIG);


        verifyNoMoreInteractions(memoryConfigStorage);
        verifyNoMoreInteractions(configurationService);
    }

}
