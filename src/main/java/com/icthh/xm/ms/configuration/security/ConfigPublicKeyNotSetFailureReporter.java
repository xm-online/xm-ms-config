package com.icthh.xm.ms.configuration.security;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalysisReporter;

public class ConfigPublicKeyNotSetFailureReporter implements FailureAnalysisReporter {

    @Override
    public void report(FailureAnalysis analysis) {
        if (analysis.getCause() instanceof ConfigPublicKeyNotSetException) {
            System.err.print(String.format("%n***************************%nAPPLICATION FAILED TO START"
                + "%n***************************%n%nDescription:%n%n%s%n%nAction:%n%n%s%n%n",
                analysis.getDescription(), analysis.getAction()));
            System.err.flush();
        }
    }
}
