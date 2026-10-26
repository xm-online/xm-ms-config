package com.icthh.xm.ms.configuration.security;

import com.icthh.xm.ms.configuration.AbstractUnitTest;
import org.junit.jupiter.api.Test;
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
    public void testReporterPrintsOnlyThisFailureToStderr() {
        FailureAnalysis analysis = new FailureAnalysis("description", "first\nsecond",
            new ConfigPublicKeyNotSetException());
        assertThat(reportToStderr(analysis))
            .contains("APPLICATION FAILED TO START")
            .contains("\ndescription\n")
            .contains("\nfirst\nsecond\n");
        assertThat(reportToStderr(new FailureAnalysis("other", "other", new IllegalStateException()))).isEmpty();
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
