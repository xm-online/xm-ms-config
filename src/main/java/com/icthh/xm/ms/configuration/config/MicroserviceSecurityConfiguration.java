package com.icthh.xm.ms.configuration.config;

import static com.icthh.xm.ms.configuration.config.Constants.CERTIFICATE;
import static com.icthh.xm.ms.configuration.config.Constants.PUBLIC_KEY;

import com.icthh.xm.commons.permission.constants.RoleConstant;
import com.icthh.xm.ms.configuration.security.DomainJwtAccessTokenConverter;
import com.icthh.xm.ms.configuration.service.TokenKeyService;
import io.github.jhipster.config.JHipsterProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@Configuration
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class MicroserviceSecurityConfiguration extends ResourceServerConfigurerAdapter {

    private final JHipsterProperties jHipsterProperties;

    private final DiscoveryClient discoveryClient;

    public MicroserviceSecurityConfiguration(JHipsterProperties jHipsterProperties,
            DiscoveryClient discoveryClient) {

        this.jHipsterProperties = jHipsterProperties;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
            .csrf()
            .disable()
            .headers()
            .frameOptions()
            .disable()
        .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
            .authorizeRequests()
            .antMatchers("/api/profile-info").permitAll()
            .antMatchers("/api/config").authenticated()
            .antMatchers("/management/health").permitAll()
            .antMatchers("/management/**").hasAuthority(RoleConstant.SUPER_ADMIN)
            .antMatchers("/swagger-resources/configuration/ui").permitAll();
    }

    @Bean
    public TokenStore tokenStore(JwtAccessTokenConverter jwtAccessTokenConverter) {
        return new JwtTokenStore(jwtAccessTokenConverter);
    }

    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter(TokenKeyService tokenKeyService)
            throws CertificateException {
        DomainJwtAccessTokenConverter converter = new DomainJwtAccessTokenConverter();
        converter.setVerifierKey(getKeyFromConfigServer(tokenKeyService));
        return converter;
    }

    @Bean
    public RestTemplate loadBalancedRestTemplate(RestTemplateCustomizer customizer) {
        RestTemplate restTemplate = new RestTemplate();
        customizer.customize(restTemplate);
        return restTemplate;
    }

    private String getKeyFromConfigServer(TokenKeyService tokenKeyService) throws CertificateException {
        String content = tokenKeyService.getKey();

        if (StringUtils.isBlank(content)) {
            throw new CertificateException("Certificate not found.");
        }

        InputStream fin = new ByteArrayInputStream(content.getBytes());

        CertificateFactory f = CertificateFactory.getInstance(CERTIFICATE);
        X509Certificate certificate = (X509Certificate)f.generateCertificate(fin);
        PublicKey pk = certificate.getPublicKey();
        return String.format(PUBLIC_KEY, new String(Base64.encode(pk.getEncoded())));
    }

}
