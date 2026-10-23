package com.icthh.xm.ms.configuration.security;

/**
 * application.config-public-key is not set, xm-ms-config can not check the private API requests.
 * {@link ConfigPublicKeyNotSetFailureAnalyzer} explains how to fix it.
 */
public class ConfigPublicKeyNotSetException extends IllegalStateException {

    public ConfigPublicKeyNotSetException() {
        super("application.config-public-key is not set: it is required to serve /api/private/v2 "
            + "without the profile migration");
    }
}
