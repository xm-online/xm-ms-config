package com.icthh.xm.ms.configuration.repository.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class InconsistentConfigLogger {

    private final AtomicBoolean configUpdateInProgress = new AtomicBoolean(false);

    void lock(String updateTriggerMethod) {
        configUpdateInProgress.set(true);
        log.info("Config update started by thread={} method={}", Thread.currentThread(), updateTriggerMethod);
    }

    void unlock() {
        configUpdateInProgress.set(false);
        log.info("Config update ended thread={}", Thread.currentThread());
    }

    void logConfigGet() {
        if (configUpdateInProgress.get()) {
            log.warn("Config get during update, possible inconsistency thread={}", Thread.currentThread());
        }
    }
}
