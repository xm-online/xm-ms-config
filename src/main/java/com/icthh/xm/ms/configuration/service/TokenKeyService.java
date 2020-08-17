package com.icthh.xm.ms.configuration.service;

import static java.lang.System.getenv;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.logging.LoggingAspectConfig;
import com.icthh.xm.ms.configuration.config.Constants;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Optional;

@Service
public class TokenKeyService {

    private static final String ENV_VAR_KEY = "PUBLIC_CER";

    private final Map<String, String> env = getenv();
    private ConfigurationService configurationService;

    public TokenKeyService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @LoggingAspectConfig(resultDetails = false)
    public String getKey() {
        if (env.get(ENV_VAR_KEY) != null) {
            return env.get(ENV_VAR_KEY);
        }

        Optional<Configuration> config = configurationService
                .findConfiguration(Constants.CONFIG + Constants.PUBLIC_KEY_FILE);
        if (!config.isPresent()) {
            return null;
        }
        Configuration configuration = config.get();

        return configuration.getContent();
    }

    @SneakyThrows
    @LoggingAspectConfig(resultDetails = false)
    public String getJwk() {
        String key = getKey();
        CertificateFactory factory = CertificateFactory.getInstance(Constants.CERTIFICATE);
        X509Certificate certificate = (X509Certificate) factory.generateCertificate(IOUtils.toInputStream(key, StandardCharsets.UTF_8));

        RSAKey.Builder builder = new RSAKey.Builder((RSAPublicKey) certificate.getPublicKey())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID("xm-web-key");
        return new JWKSet(builder.build()).toJSONObject().toJSONString();
    }
}
