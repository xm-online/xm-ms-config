package com.icthh.xm.ms.configuration.domain;

import java.util.Objects;

/**
 * Application request source type enumeration.
 */
public enum RequestSourceType {

    WEB_SERVICE("ws"),
    SYSTEM_QUEUE("sysqueue"),
    CONFIG_QUEUE("confqueue");

    private final String name;

    RequestSourceType(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getName() {
        return name;
    }

}
