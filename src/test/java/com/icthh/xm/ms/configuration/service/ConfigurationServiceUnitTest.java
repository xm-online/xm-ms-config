package com.icthh.xm.ms.configuration.service;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.configuration.repository.PersistenceConfigRepository;
import com.icthh.xm.ms.configuration.repository.impl.InMemoryRepository;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationServiceUnitTest {

    private ConfigurationService configurationService;
    @InjectMocks
    private InMemoryRepository inMemoryRepository;
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
        configurationService = new ConfigurationService(inMemoryRepository, tenantContextHolder);
    }

    @Test
    public void updateConfiguration() {
        Configuration configuration = new Configuration("path", "content", "commit");

        configurationService.updateConfiguration(configuration);

        verify(persistenceConfigRepository).save(configuration, null);
    }

    @Test
    public void updateConfigurationWithHash() {
        Configuration configuration = new Configuration("path", "content", "commit");

        configurationService.updateConfiguration(configuration, "hash");

        verify(persistenceConfigRepository).save(configuration, "hash");
    }

    @Test
    public void findConfiguration() {
        configurationService.findConfiguration("path");

        verifyZeroInteractions(persistenceConfigRepository);
    }

    @Test
    public void getConfigurations() {
        Configuration configuration = new Configuration("path", "content", "commit");
        inMemoryRepository.getMap(null).put(configuration.getPath(), configuration);

        List<Configuration> result = configurationService.getConfigurations();

        assertThat(result).containsExactly(configuration);
        verifyZeroInteractions(persistenceConfigRepository);
    }

    @Test
    public void deleteConfiguration() {
        configurationService.deleteConfiguration("path");

        verify(persistenceConfigRepository).delete("path");
    }

    @Test
    public void refreshConfigurations() {
        configurationService.refreshConfiguration();

        verify(persistenceConfigRepository).findAll();
    }

    @Test
    public void createConfigurations() {
        configurationService.createConfigurations(singletonList(new MockMultipartFile("test", "path", "contentType", "content".getBytes())));

        verify(persistenceConfigRepository).saveAll(singletonList(new Configuration("path", "content", null)));
    }

    @Test
    public void refreshConfiguration() {
        when(persistenceConfigRepository.find("path")).thenReturn(new Configuration("path", "content", null));
        configurationService.refreshConfiguration("path");
    }

    @Test
    public void refreshTenantConfigurations() {
        configurationService.refreshTenantConfigurations();

        verify(persistenceConfigRepository).findAll();
    }
}