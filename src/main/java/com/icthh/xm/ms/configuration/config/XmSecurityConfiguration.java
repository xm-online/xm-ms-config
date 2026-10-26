package com.icthh.xm.ms.configuration.config;

import static com.icthh.xm.ms.configuration.security.PrivateApiAccessConfiguration.OLD_PRIVATE_API_ACCESS;

import com.icthh.xm.commons.permission.access.XmPermissionEvaluator;
import com.icthh.xm.commons.security.RoleConstant;
import com.icthh.xm.commons.security.XmPermissionCheckService;
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
    private final boolean apiDocsPrivilegeCheck;

    public XmSecurityConfiguration(TokenProvider tokenProvider,
                                   @Value("${jhipster.security.content-security-policy}")
                                   String contentSecurityPolicy,
                                   @Qualifier(OLD_PRIVATE_API_ACCESS)
                                   AuthorizationManager<RequestAuthorizationContext> oldPrivateApiAccess,
                                   ConfigClientTokenAuthorizationManager configClientToken,
                                   XmPermissionCheckService permissionCheckService,
                                   @Value("${application.api-docs.privilege-check:true}")
                                   boolean apiDocsPrivilegeCheck) {
        super(tokenProvider, contentSecurityPolicy, permissionCheckService);
        this.oldPrivateApiAccess = oldPrivateApiAccess;
        this.configClientToken = configClientToken;
        this.apiDocsPrivilegeCheck = apiDocsPrivilegeCheck;
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
                .requestMatchers("/api/token_key").permitAll()
        );
        if (!apiDocsPrivilegeCheck) {
            http.authorizeHttpRequests(auth ->
                auth.requestMatchers("/v3/api-docs/**").hasAuthority(RoleConstant.SUPER_ADMIN)
            );
        }
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
