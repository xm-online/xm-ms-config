package com.icthh.xm.ms.configuration.web.rest.vm;

import ch.qos.logback.classic.Logger;
import lombok.Getter;
import lombok.Setter;

/**
 * View Model object for storing a Logback logger.
 */
public class LoggerVM {

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String level;

    public LoggerVM(Logger logger) {
        this.name = logger.getName();
        this.level = logger.getEffectiveLevel().toString();
    }

    public LoggerVM() {
        // Empty public constructor used by Jackson.
    }

    @Override
    public String toString() {
        return "LoggerVM{" +
            "name='" + name + '\'' +
            ", level='" + level + '\'' +
            '}';
    }
}
