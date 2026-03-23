package com.icthh.xm.ms.configuration.config.export;

import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.security.internal.SpringSecurityXmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.internal.DefaultTenantContextHolder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.config.BeanConfiguration;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import com.icthh.xm.ms.configuration.repository.kafka.ConfigTopicProducer;
import com.icthh.xm.ms.configuration.service.ConfigVersionDeserializer;
import com.icthh.xm.ms.configuration.service.ConfigurationService;
import com.icthh.xm.ms.configuration.service.FileService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeService;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeStorage;
import com.icthh.xm.ms.configuration.service.VersionCache;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("export")
@EnableConfigurationProperties({ApplicationProperties.class})
@Import({
    ConfigurationService.class,
    ConfigVersionDeserializer.class,
    VersionCache.class,
    FileService.class,
    TenantAliasTreeStorage.class,
    TenantAliasTreeService.class
})
public class ExportBeanConfiguration extends BeanConfiguration {

    @Bean
    public ConfigTopicProducer configTopicProducer() {
        return new ConfigTopicProducer(null, null) {
            @Override
            public void notifyConfigurationChanged(ConfigVersion version, List<String> paths) {
                // Nothing to send
            }
        };
    }

    @Bean
    public TenantContextHolder tenantContextHolder() {
        return new DefaultTenantContextHolder();
    }

    @Bean
    public XmAuthenticationContextHolder authenticationContextHolder() {
        return new SpringSecurityXmAuthenticationContextHolder();
    }
}
