package com.icthh.xm.ms.configuration.security;

import com.icthh.xm.ms.configuration.AbstractUnitTest;
import io.jsonwebtoken.Jwts;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static com.icthh.xm.commons.config.client.repository.ConfigPrivateApi.AUDIENCE;
import static com.icthh.xm.commons.config.client.repository.ConfigPrivateApi.TOKEN_HEADER;
import static com.icthh.xm.commons.config.client.repository.ConfigPrivateApi.TOKEN_TTL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigClientTokenAuthorizationManagerUnitTest extends AbstractUnitTest {

    @Test
    public void testMissingPublicKeyFailsWithoutMigration() {
        assertThatThrownBy(() -> new ConfigClientTokenAuthorizationManager(false, " "))
            .isInstanceOf(ConfigPublicKeyNotSetException.class)
            .hasMessageContaining("application.config-public-key");
    }

    @Test
    public void testInvalidPublicKeyFailsWithPropertyName() {
        assertThatThrownBy(() -> new ConfigClientTokenAuthorizationManager(false, "not-a-key"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("application.config-public-key");
        assertThatThrownBy(() -> new ConfigClientTokenAuthorizationManager(true, "bm90LWEta2V5"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("application.config-public-key");
    }

    @Test
    public void testMissingPublicKeyIsAllowedWithMigration() {
        assertThatNoException().isThrownBy(() -> new ConfigClientTokenAuthorizationManager(true, null));
    }

    @Test
    @SneakyThrows
    public void testTokenValidLongerThanTokenTtlIsRejected() {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ConfigClientTokenAuthorizationManager manager = new ConfigClientTokenAuthorizationManager(false,
            Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));

        assertThat(isGranted(manager, token(keys.getPrivate(), TOKEN_TTL))).isTrue();
        assertThat(isGranted(manager, token(keys.getPrivate(), Duration.ofDays(3650)))).isFalse();
    }

    @Test
    public void testReporterPrintsOnlyThisFailureToStderr() {
        FailureAnalysis analysis = new FailureAnalysis("description", "first\nsecond",
            new ConfigPublicKeyNotSetException());
        assertThat(reportToStderr(analysis))
            .contains("APPLICATION FAILED TO START")
            .contains("\ndescription\n")
            .contains("\nfirst\nsecond\n");
        assertThat(reportToStderr(new FailureAnalysis("other", "other", new IllegalStateException()))).isEmpty();
    }

    private static String token(PrivateKey privateKey, Duration ttl) {
        return Jwts.builder()
            .audience().add(AUDIENCE).and()
            .expiration(Date.from(Instant.now().plus(ttl)))
            .signWith(privateKey, Jwts.SIG.EdDSA)
            .compact();
    }

    private static boolean isGranted(ConfigClientTokenAuthorizationManager manager, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TOKEN_HEADER, token);
        return manager.authorize(() -> null, new RequestAuthorizationContext(request)).isGranted();
    }

    private static String reportToStderr(FailureAnalysis analysis) {
        PrintStream stderr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setErr(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            new ConfigPublicKeyNotSetFailureReporter().report(analysis);
        } finally {
            System.setErr(stderr);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
