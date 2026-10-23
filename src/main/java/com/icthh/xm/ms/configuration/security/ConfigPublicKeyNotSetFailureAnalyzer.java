package com.icthh.xm.ms.configuration.security;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Explains on startup how to run xm-ms-config without application.config-public-key and how to set it.
 */
public class ConfigPublicKeyNotSetFailureAnalyzer extends AbstractFailureAnalyzer<ConfigPublicKeyNotSetException> {

    static final String DESCRIPTION = """
        application.config-public-key is not set: xm-ms-config checks the requests to /api/private/v2 \
        with this public key.""";

    static final String ACTION = """
        1) Local development without keys: run with the profile migration \
        (the microservices with the profile migration too), by one of:
        - gradlew: `./gradlew bootRun --args='--spring.profiles.active=dev,migration'`;
        - environment variable: `SPRING_PROFILES_INCLUDE=migration ./gradlew bootRun`;
        - jar or container: `SPRING_PROFILES_ACTIVE=prod,migration`.
        2) For production use: generate one key pair per environment with OpenSSL 3 \
        (macOS: `brew install openssl`, the system openssl has no ed25519):
        - `openssl genpkey -algorithm ed25519 -outform DER -out private.der`;
        - private key: `base64 < private.der | tr -d '\\n'`;
        - public key: `openssl pkey -inform DER -in private.der -pubout -outform DER | base64 | tr -d '\\n'`;
        - `rm private.der`.
        Other ways are in README, section "Keys for the private config API".
        Set the public key in xm-ms-config by one of:
        - environment variable: `APPLICATION_CONFIG_PUBLIC_KEY=<public key>`;
        - gradlew: `./gradlew bootRun --args='--spring.profiles.active=dev --application.config-public-key=<public key>'`;
        - application-dev.yml: `config-public-key: <public key>` under `application:`.
        Set the private key in every other microservice: application.config-private-key \
        (APPLICATION_CONFIG_PRIVATE_KEY).""";

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ConfigPublicKeyNotSetException cause) {
        return new FailureAnalysis(DESCRIPTION, ACTION, cause);
    }
}
