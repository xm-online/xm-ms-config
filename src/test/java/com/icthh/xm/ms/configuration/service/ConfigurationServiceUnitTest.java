package com.icthh.xm.ms.configuration.service;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.repository.impl.ConfigProxyRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationServiceUnitTest {

    @InjectMocks
    private ConfigurationService configurationService;
    @Mock
    private ConfigProxyRepository configProxyRepository;
    @Mock
    private TenantContextHolder tenantContextHolder;
    @Mock
    private TenantContext tenantContext;

    @Before
    public void before() {
        when(tenantContext.getTenantKey()).thenReturn(Optional.of(TenantKey.valueOf("tenant")));
        when(tenantContextHolder.getContext()).thenReturn(tenantContext);
    }

    @Test
    public void createConfigurations() {
        configurationService.createConfigurations(
            singletonList(new MockMultipartFile("test", "path", "contentType", "content".getBytes())));

        verify(configProxyRepository).saveAll(singletonList(new Configuration("path", "content")));
    }

    @Test
    public void updateConfiguration() {
        Configuration configuration = new Configuration("path", "content");

        configurationService.updateConfiguration(configuration);

        verify(configProxyRepository).save(configuration, null);
    }

    @Test
    public void updateConfigurationWithHash() {
        Configuration configuration = new Configuration("path", "content");

        configurationService.updateConfiguration(configuration, "hash");

        verify(configProxyRepository).save(configuration, "hash");
    }

    @Test
    public void findConfiguration() {
        Configuration configuration = new Configuration("path", "content");
        when(configProxyRepository.find("path", null)).thenReturn(new ConfigurationItem("commit", configuration));

        Optional<Configuration> result = configurationService.findConfiguration("path");

        assertThat(result.get()).isEqualTo(configuration);
    }

    @Test
    public void deleteConfiguration() {
        configurationService.deleteConfiguration("path");

        verify(configProxyRepository).delete("path");
    }

    @Test
    public void refreshConfigurations() {
        configurationService.refreshConfiguration();

        verify(configProxyRepository).refreshAll();
    }

    @Test
    public void recloneConfigurations() {
        configurationService.recloneConfiguration();

        verify(configProxyRepository).recloneConfiguration();
    }

    @Test
    public void refreshConfiguration() {
        configurationService.refreshConfiguration("path");

        verify(configProxyRepository).refreshPath("path");
    }

    @Test
    public void refreshTenantConfigurations() {
        configurationService.refreshTenantConfigurations();

        verify(configProxyRepository).refreshTenant("tenant");
    }
}
