package com.icthh.xm.ms.configuration.security;

import com.icthh.xm.ms.configuration.AbstractUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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
    public void testMissingPublicKeyIsAllowedWithMigration() {
        assertThatNoException().isThrownBy(() -> new ConfigClientTokenAuthorizationManager(true, null));
    }

    @Test
    public void testFailureAnalyzerExplainsMigrationProfileAndKeys() {
        FailureAnalysis analysis = analyze(new ConfigPublicKeyNotSetException());

        assertThat(analysis.getDescription()).contains("application.config-public-key is not set");
        assertThat(analysis.getAction())
            .contains("`./gradlew bootRun --args='--spring.profiles.active=dev,migration'`")
            .contains("`SPRING_PROFILES_INCLUDE=migration ./gradlew bootRun`")
            .contains("For production use")
            .contains("`openssl genpkey -algorithm ed25519 -outform DER -out private.der`")
            .contains("`APPLICATION_CONFIG_PUBLIC_KEY=<public key>`")
            .contains("--application.config-public-key=<public key>")
            .contains("application-dev.yml: `config-public-key: <public key>` under `application:`");
    }

    @Test
    public void testReporterPrintsOnlyThisFailureToStderr() {
        assertThat(reportToStderr(analyze(new ConfigPublicKeyNotSetException())))
            .contains("APPLICATION FAILED TO START")
            .contains("application.config-public-key is not set")
            .contains("\n- environment variable: `SPRING_PROFILES_INCLUDE=migration ./gradlew bootRun`;\n");
        assertThat(reportToStderr(new FailureAnalysis("other", "other", new IllegalStateException()))).isEmpty();
    }

    private static FailureAnalysis analyze(Throwable cause) {
        return new ConfigPublicKeyNotSetFailureAnalyzer()
            .analyze(new BeanCreationException("configClientToken", "failed", cause));
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
