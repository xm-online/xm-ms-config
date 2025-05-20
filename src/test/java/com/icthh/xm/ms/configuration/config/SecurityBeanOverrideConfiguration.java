package com.icthh.xm.ms.configuration.config;

import org.springframework.cloud.client.loadbalancer.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

/**
 * Overrides UAA specific beans, so they do not interfere the testing
 * This configuration must be included in @SpringBootTest in order to take effect.
 */
@Configuration
public class SecurityBeanOverrideConfiguration {

    @Bean
    @Primary
    public RestTemplate loadBalancedRestTemplate(RestTemplateCustomizer customizer) {
        return null;
    }
}
