package com.icthh.xm.ms.configuration.security;

public class ConfigPublicKeyNotSetException extends IllegalStateException {

    public ConfigPublicKeyNotSetException() {
        super("application.config-public-key is not set: it is required to serve /api/private/v2 "
            + "without the profile migration");
    }
}
