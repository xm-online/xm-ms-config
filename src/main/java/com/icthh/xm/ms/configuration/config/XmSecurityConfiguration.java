package com.icthh.xm.ms.configuration.config;

import com.icthh.xm.commons.permission.access.XmPermissionEvaluator;
import com.icthh.xm.commons.security.jwt.TokenProvider;
import com.icthh.xm.commons.security.spring.config.SecurityConfiguration;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
public class XmSecurityConfiguration extends SecurityConfiguration {

    public XmSecurityConfiguration(TokenProvider tokenProvider,
                                   @Value("${jhipster.security.content-security-policy}")
                                   String contentSecurityPolicy) {
        super(tokenProvider, contentSecurityPolicy);
    }

    @Override
    @SneakyThrows
    protected HttpSecurity applyUrlSecurity(HttpSecurity http) {
        http.authorizeHttpRequests(auth ->
            auth
                .requestMatchers("/api/private/config_map").permitAll()
                .requestMatchers("/api/private/config_map/pattern").permitAll()
                .requestMatchers("/api/private/config").permitAll()
                .requestMatchers("/api/profile/webapp/settings-public.yml").permitAll()
                .requestMatchers("/api/profile/webapp/public/**").permitAll()
                .requestMatchers("/api/configs_hash").permitAll()
                .requestMatchers("/api/profile-info").permitAll()
                .requestMatchers("/api/token_key").permitAll()
        );
        return super.applyUrlSecurity(http);
    }

    @Bean
    @Primary
    static MethodSecurityExpressionHandler expressionHandler(XmPermissionEvaluator customPermissionEvaluator) {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(customPermissionEvaluator);
        return expressionHandler;
    }
}
