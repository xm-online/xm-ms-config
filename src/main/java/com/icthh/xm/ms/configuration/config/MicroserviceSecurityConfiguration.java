package com.icthh.xm.ms.configuration.config;

import com.icthh.xm.commons.permission.constants.RoleConstant;
import com.icthh.xm.commons.security.oauth2.OAuth2JwtAccessTokenConverter;
import com.icthh.xm.commons.security.oauth2.OAuth2Properties;
import com.icthh.xm.commons.security.oauth2.OAuth2SignatureVerifierClient;
import com.icthh.xm.ms.configuration.service.TokenKeyService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.loadbalancer.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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

import static com.icthh.xm.ms.configuration.config.Constants.CERTIFICATE;
import static com.icthh.xm.ms.configuration.config.Constants.PUBLIC_KEY;

@Configuration
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class MicroserviceSecurityConfiguration extends ResourceServerConfigurerAdapter {

    private final OAuth2Properties oAuth2Properties;

    public MicroserviceSecurityConfiguration(OAuth2Properties oAuth2Properties) {
        this.oAuth2Properties = oAuth2Properties;
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
            .antMatchers("/management/prometheus/**").permitAll()
            .antMatchers("/management/**").hasAuthority(RoleConstant.SUPER_ADMIN)
            .antMatchers("/swagger-resources/configuration/ui").permitAll();
    }

    @Bean
    public TokenStore tokenStore(JwtAccessTokenConverter jwtAccessTokenConverter) {
        return new JwtTokenStore(jwtAccessTokenConverter);
    }

    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter(OAuth2SignatureVerifierClient signatureVerifierClient) {
        return new OAuth2JwtAccessTokenConverter(oAuth2Properties, signatureVerifierClient);
    }

    @Bean
    public RestTemplate loadBalancedRestTemplate(RestTemplateCustomizer customizer) {
        RestTemplate restTemplate = new RestTemplate();
        customizer.customize(restTemplate);
        return restTemplate;
    }

    private String getKeyFromConfigServer(TokenKeyService tokenKeyService) throws CertificateException, IOException {
        String content = tokenKeyService.getKey();

        if (StringUtils.isBlank(content)) {
            throw new CertificateException("Certificate not found.");
        }

        try (InputStream fin = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {

            CertificateFactory f = CertificateFactory.getInstance(CERTIFICATE);
            X509Certificate certificate = (X509Certificate) f.generateCertificate(fin);
            PublicKey pk = certificate.getPublicKey();
            return String.format(PUBLIC_KEY, Base64.encodeBase64String(pk.getEncoded()));
        }
    }

}
