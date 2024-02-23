package com.icthh.xm.ms.configuration.service;

import static com.icthh.xm.ms.configuration.config.Constants.CONFIG;
import static com.icthh.xm.ms.configuration.config.Constants.TENANTS;
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
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.domain.ConfigurationItem;
import com.icthh.xm.ms.configuration.domain.ConfigurationList;
import com.icthh.xm.ms.configuration.repository.impl.ConfigProxyRepository;
import com.icthh.xm.ms.configuration.service.dto.ConfigurationHashSum;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationServiceUnitTest {

    public static final String TENANT_NAME = "tenant";
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
        when(tenantContext.getTenantKey()).thenReturn(Optional.of(TenantKey.valueOf(TENANT_NAME)));
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
        when(configProxyRepository.find("path")).thenReturn(new ConfigurationItem(new ConfigVersion("commit"), configuration));

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

        verify(configProxyRepository).refreshTenant(TENANT_NAME);
    }

    @Test
    public void findConfigurations() {
        String firstPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc1";
        String secondPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc2";
        String thirdPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc3";
        Configuration firstConfig = new Configuration(firstPath, "firstContent");
        Configuration secondConfig = new Configuration(secondPath, "secondContent");
        Configuration thirdConfig = new Configuration(thirdPath, "thirdContent");

        when(configProxyRepository.findAll()).thenReturn(new ConfigurationList(new ConfigVersion("commit"), List.of(firstConfig, secondConfig, thirdConfig)));

        Map<String, Configuration> actual = configurationService.findConfigurations(List.of(firstPath, secondPath), false);

        assertThat(actual).hasSize(2);
        assertThat(actual.get(firstPath)).isEqualTo(firstConfig);
        assertThat(actual.get(secondPath)).isEqualTo(secondConfig);

        verify(configProxyRepository).findAll();
        verifyNoMoreInteractions(configProxyRepository);
    }

    @Test
    public void findConfigurationsAll() {
        String firstPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc1";
        String secondPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc2";
        String thirdPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc3";
        String fourthPath = CONFIG + TENANTS + "/ANOTHERTENANT/doc4";
        Configuration firstConfig = new Configuration(firstPath, "firstContent");
        Configuration secondConfig = new Configuration(secondPath, "secondContent");
        Configuration thirdConfig = new Configuration(thirdPath, "thirdContent");
        Configuration fourthConfig = new Configuration(fourthPath, "fourthContent");
        when(configProxyRepository.findAll()).thenReturn(new ConfigurationList(new ConfigVersion("commit"), List.of(firstConfig, secondConfig, thirdConfig, fourthConfig)));

        Map<String, Configuration> actual = configurationService.findConfigurations(List.of(), true);

        assertThat(actual).hasSize(3);
        assertThat(actual.get(firstPath)).isEqualTo(firstConfig);
        assertThat(actual.get(secondPath)).isEqualTo(secondConfig);
        assertThat(actual.get(thirdPath)).isEqualTo(thirdConfig);

        verify(configProxyRepository).findAll();
        verifyNoMoreInteractions(configProxyRepository);
    }

    @Test
    public void findConfigurationsIfPathsEmpty() {
        Map<String, Configuration> actual = configurationService.findConfigurations(Collections.emptyList(), false);

        assertThat(actual).hasSize(0);

        verifyZeroInteractions(configProxyRepository);
    }

    @Test
    public void findConfigurationsHashSum() {
        String firstPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc1";
        String secondPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc2";
        String thirdPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc3";
        String fourthPath = CONFIG + TENANTS + "/ANOTHERTENANT/doc4";
        Configuration firstConfig = new Configuration(firstPath, "firstContent");
        Configuration secondConfig = new Configuration(secondPath, "secondContent");
        Configuration thirdConfig = new Configuration(thirdPath, "thirdContent");
        Configuration fourthConfig = new Configuration(fourthPath, "fourthContent");
        when(configProxyRepository.findAll()).thenReturn(new ConfigurationList(new ConfigVersion("commit"), List.of(firstConfig, secondConfig, thirdConfig, fourthConfig)));

        List<ConfigurationHashSum> configurationsHashSum = configurationService.findConfigurationsHashSum().getConfigurationsHashSum();

        assertThat(configurationsHashSum).hasSize(3)
            .extracting(ConfigurationHashSum::getPath)
            .containsExactlyInAnyOrder(firstPath, secondPath, thirdPath);

        assertThat(configurationsHashSum).extracting(ConfigurationHashSum::getHashSum).isNotEmpty();

        verify(configProxyRepository).findAll();
        verifyNoMoreInteractions(configProxyRepository);
    }

    @Test
    public void updateConfigurationsFromList() {
        String firstPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc1";
        String secondPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc2";
        String thirdPath = CONFIG + TENANTS + "/" + TENANT_NAME + "/doc3";
        String fourthPath = CONFIG + TENANTS + "/ANOTHERTENANT/doc4";
        Configuration firstConfig = new Configuration(firstPath, "updateContent");
        Configuration secondConfig = new Configuration(secondPath, "");
        Configuration thirdConfig = new Configuration(thirdPath, "updateContent");
        Configuration fourthConfig = new Configuration(fourthPath, "updateContent");

        configurationService.updateConfigurationsFromList(List.of(firstConfig, secondConfig, thirdConfig, fourthConfig));

        verify(configProxyRepository).saveOrDeleteEmpty(List.of(firstConfig, secondConfig, thirdConfig));
        verify(configProxyRepository).refreshTenant(TENANT_NAME);
        verifyNoMoreInteractions(configProxyRepository);

    }
}
