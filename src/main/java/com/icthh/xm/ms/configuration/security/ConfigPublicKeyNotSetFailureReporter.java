package com.icthh.xm.ms.configuration.security;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalysisReporter;

/**
 * Prints {@link ConfigPublicKeyNotSetFailureAnalyzer} to stderr with line breaks: the log keeps it in one line.
 */
public class ConfigPublicKeyNotSetFailureReporter implements FailureAnalysisReporter {

    @Override
    public void report(FailureAnalysis analysis) {
        if (analysis.getCause() instanceof ConfigPublicKeyNotSetException) {
            // one write, so the log lines of stdout do not get in the middle
            System.err.print(String.format("%n***************************%nAPPLICATION FAILED TO START"
                + "%n***************************%n%nDescription:%n%n%s%n%nAction:%n%n%s%n%n",
                analysis.getDescription(), analysis.getAction()));
            System.err.flush();
        }
    }
}
