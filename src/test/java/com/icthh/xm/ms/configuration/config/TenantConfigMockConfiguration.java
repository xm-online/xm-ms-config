package com.icthh.xm.ms.configuration.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.config.client.config.XmConfigProperties;
import com.icthh.xm.commons.config.client.listener.ApplicationReadyEventListener;
import com.icthh.xm.commons.config.client.repository.CommonConfigRepository;
import com.icthh.xm.commons.config.client.repository.TenantConfigRepository;
import com.icthh.xm.commons.config.client.repository.TenantListRepository;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Primary;

@Configuration
public class TenantConfigMockConfiguration {

    private Set<String> tenants = new HashSet<>();

    {
        tenants.add("XM");
        tenants.add("DEMO");
        tenants.add("TEST");
        tenants.add("RESINTTEST");
    }

    @Bean
    public TenantAliasTreeStorage tenantAliasTreeStorage(TenantContextHolder tenantContextHolder) {
        return new TenantAliasTreeStorage(tenantContextHolder);
    }

    @Bean
    public TenantAliasTreeService tenantAliasService(ConfigurationService configurationService,
                                                     TenantAliasTreeStorage tenantAliasTreeStorage) {
        return new TenantAliasTreeService(configurationService, tenantAliasTreeStorage);
    }

    @Bean
    @Primary
    public TenantListRepository tenantListRepository() {
        TenantListRepository mockTenantListRepository = mock(TenantListRepository.class);
        doAnswer(mvc -> tenants.add(mvc.getArguments()[0].toString())).when(mockTenantListRepository).addTenant(any());
        doAnswer(mvc -> tenants.remove(mvc.getArguments()[0].toString())).when(mockTenantListRepository).deleteTenant(any());
        when(mockTenantListRepository.getTenants()).thenReturn(tenants);
        return mockTenantListRepository;
    }

    @Bean
    public XmConfigProperties xmConfigProperties() {
        return mock(XmConfigProperties.class);
    }

    @Bean
    public TenantConfigRepository tenantConfigRepository() {
        return mock(TenantConfigRepository.class);
    }

    @Bean
    public CommonConfigRepository commonConfigRepository() {
        return mock(CommonConfigRepository.class);
    }

    @Bean
    public ApplicationReadyEventListener applicationReadyEventListener(){
        return mock(ApplicationReadyEventListener.class);
    }

}
