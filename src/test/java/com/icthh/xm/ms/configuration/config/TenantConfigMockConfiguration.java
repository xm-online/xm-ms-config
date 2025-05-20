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
import com.icthh.xm.commons.security.jwt.TokenProvider;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeStorage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.prometheus.client.CollectorRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

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
    public CollectorRegistry collectorRegistry() {
        return new CollectorRegistry();
    }

    @Bean
    public TenantListRepository tenantListRepository() {
        TenantListRepository mockTenantListRepository = mock(TenantListRepository.class);
        doAnswer(mvc -> tenants.add(mvc.getArguments()[0].toString())).when(mockTenantListRepository).addTenant(any());
        doAnswer(mvc -> tenants.remove(mvc.getArguments()[0].toString())).when(mockTenantListRepository).deleteTenant(any());
        when(mockTenantListRepository.getTenants()).thenReturn(tenants);
        return mockTenantListRepository;
    }

    @Bean
    public TenantConfigRepository tenantConfigRepository() {
        return mock(TenantConfigRepository.class);
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
    public XmConfigProperties xmConfigProperties() {
        return mock(XmConfigProperties.class);
    }

    @Bean
    public CommonConfigRepository commonConfigRepository() {
        return mock(CommonConfigRepository.class);
    }

    @Bean
    public ApplicationReadyEventListener applicationReadyEventListener(){
        return mock(ApplicationReadyEventListener.class);
    }

    public static AtomicBoolean failOnRefresh = new AtomicBoolean(false);
    @Bean
    @Primary
    public ConfigTopicProducer configTopicProducer() {
        return new ConfigTopicProducer(null, null) {
            @Override
            public void notifyConfigurationChanged(ConfigVersion version, List<String> paths) {
                if (failOnRefresh.get()) {
                    throw new RuntimeException("notifyConfigurationChanged not implemented");
                }
            }
        };
    }

    @Qualifier("xm-config-rest-template")
    @Bean
    public RestTemplate restTemplate() {
        return mock(RestTemplate.class);
    }

    @Bean
    @Primary
    public TokenProvider tokenProvider() {
        return mock(TokenProvider.class);
    }

    @Bean
    @Primary
    public TenantContextHolder tenantContextHolder() {
        return mock(TenantContextHolder.class);
    }
}
