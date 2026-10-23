package com.icthh.xm.ms.configuration.config;

import static com.icthh.xm.ms.configuration.security.OldPrivateApiAccessConfiguration.OLD_PRIVATE_API_ACCESS;

import com.icthh.xm.commons.permission.access.XmPermissionEvaluator;
import com.icthh.xm.commons.permission.constants.RoleConstant;
import com.icthh.xm.commons.security.jwt.TokenProvider;
import com.icthh.xm.commons.security.spring.config.SecurityConfiguration;
import com.icthh.xm.ms.configuration.security.ConfigClientTokenAuthorizationManager;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
public class XmSecurityConfiguration extends SecurityConfiguration {

    private final AuthorizationManager<RequestAuthorizationContext> oldPrivateApiAccess;
    private final ConfigClientTokenAuthorizationManager configClientToken;

    public XmSecurityConfiguration(TokenProvider tokenProvider,
                                   @Value("${jhipster.security.content-security-policy}")
                                   String contentSecurityPolicy,
                                   @Qualifier(OLD_PRIVATE_API_ACCESS)
                                   AuthorizationManager<RequestAuthorizationContext> oldPrivateApiAccess,
                                   ConfigClientTokenAuthorizationManager configClientToken) {
        super(tokenProvider, contentSecurityPolicy);
        this.oldPrivateApiAccess = oldPrivateApiAccess;
        this.configClientToken = configClientToken;
    }

    @Override
    @SneakyThrows
    protected HttpSecurity applyUrlSecurity(HttpSecurity http) {
        http.authorizeHttpRequests(auth ->
            auth
                .requestMatchers("/api/private/config_map").access(oldPrivateApiAccess)
                .requestMatchers("/api/private/config_map/pattern").access(oldPrivateApiAccess)
                .requestMatchers("/api/private/v2/config_map").access(configClientToken)
                .requestMatchers("/api/private/v2/config_map/pattern").access(configClientToken)
                .requestMatchers("/api/profile/webapp/settings-public.yml").permitAll()
                .requestMatchers("/api/profile/webapp/public/**").permitAll()
                .requestMatchers("/api/profile-info").permitAll()
                .requestMatchers("/api/token_key").permitAll()
                .requestMatchers("/v3/api-docs/**").hasAuthority(RoleConstant.SUPER_ADMIN)
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
