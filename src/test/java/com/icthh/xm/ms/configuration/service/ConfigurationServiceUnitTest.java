package com.icthh.xm.ms.configuration.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.domain.Configurations;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.ProxyRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationServiceUnitTest {

    private ConfigurationService configurationService;
    @InjectMocks
    private ProxyRepository proxyRepository;
    @Mock
    private PersistenceConfigRepository persistenceConfigRepository;
    @Mock
    private TenantContextHolder tenantContextHolder;
    @Mock
    private ConfigTopicProducer configTopicProducer;
    @Mock
    private TenantContext tenantContext;

    @Before
    public void before() {
        when(tenantContext.getTenantKey()).thenReturn(Optional.of(TenantKey.valueOf("tenant")));
        when(tenantContextHolder.getContext()).thenReturn(tenantContext);
        configurationService = new ConfigurationService(proxyRepository, tenantContextHolder);
    }

    @Test
    public void createConfigurations() {
        when(persistenceConfigRepository.saveAll(singletonList(new Configuration("path", "content", null)))).thenReturn("commit");
        configurationService.createConfigurations(singletonList(new MockMultipartFile("test", "path", "contentType", "content".getBytes())));

        verify(configTopicProducer).notifyConfigurationChanged("commit", singletonList("path"));
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void updateConfiguration() {
        Configuration configuration = new Configuration("path", "content", "commit");
        when(persistenceConfigRepository.save(configuration, null)).thenReturn("commit");

        configurationService.updateConfiguration(configuration);

        verify(configTopicProducer).notifyConfigurationChanged("commit", singletonList("path"));
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void updateConfigurationWithHash() {
        Configuration configuration = new Configuration("path", "content", "commit");
        when(persistenceConfigRepository.save(configuration, "hash")).thenReturn("commit");

        configurationService.updateConfiguration(configuration, "hash");

        verify(configTopicProducer).notifyConfigurationChanged("commit", singletonList("path"));
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void findConfiguration() {
        configurationService.findConfiguration("path");

        verifyZeroInteractions(persistenceConfigRepository, configTopicProducer);
    }

    @Test
    public void getConfigurations() {
        Configuration configuration = new Configuration("path", "content", "commit");
        proxyRepository.getMap(null).put(configuration.getPath(), configuration);

        List<Configuration> result = configurationService.getConfigurations();

        assertThat(result).containsExactly(configuration);
        verifyZeroInteractions(persistenceConfigRepository, configTopicProducer);
    }

    @Test
    public void deleteConfiguration() {
        configurationService.deleteConfiguration("path");

        verify(persistenceConfigRepository).delete("path");
        verify(configTopicProducer).notifyConfigurationChanged(null, singletonList("path"));
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void refreshConfigurations() {
        when(persistenceConfigRepository.findAll()).thenReturn(new Configurations("commit", singletonList(new Configuration("path", "content", "commit"))));
        configurationService.refreshConfiguration();

        verify(configTopicProducer).notifyConfigurationChanged("commit", singletonList("path"));
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void refreshConfiguration() {
        when(persistenceConfigRepository.find("path")).thenReturn(new Configuration("path", "content", "commit"));
        configurationService.refreshConfiguration("path");

        verify(configTopicProducer).notifyConfigurationChanged("commit", singletonList("path"));
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void refreshTenantConfigurations() {
        when(persistenceConfigRepository.findAll()).thenReturn(new Configurations("commit", singletonList(new Configuration("path", "content", "commit"))));

        configurationService.refreshTenantConfigurations();

        verify(persistenceConfigRepository).findAll();
        verify(configTopicProducer).notifyConfigurationChanged("commit", emptyList());
        verifyNoMoreInteractions(configTopicProducer);
    }

    @Test
    public void refreshTenantConfigurationsWithPath() {
        when(persistenceConfigRepository.findAll()).thenReturn(new Configurations("commit", singletonList(new Configuration("/config/tenants/tenant/path", "content", "commit"))));

        configurationService.refreshTenantConfigurations();

        verify(persistenceConfigRepository).findAll();
        verify(configTopicProducer).notifyConfigurationChanged("commit", singletonList("/config/tenants/tenant/path"));
        verifyNoMoreInteractions(configTopicProducer);
    }
}