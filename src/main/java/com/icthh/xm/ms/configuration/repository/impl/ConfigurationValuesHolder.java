package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;

public class ConfigurationValuesHolder {

    @Getter
    private final ConcurrentHashMap<String, Configuration> storage = new ConcurrentHashMap<>();
    @Getter
    private final ConcurrentHashMap<String, Configuration> processedStorage = new ConcurrentHashMap<>();
    @Getter
    private final ConcurrentHashMap<String, Configuration> privateStorage = new ConcurrentHashMap<>();

    // Snapshot of combined configurations
    private final AtomicReference<Map<String, Configuration>> storageConfigSnapshot = new AtomicReference<>(
        new HashMap<>());
    private final AtomicReference<Map<String, Configuration>> privateConfigSnapshot = new AtomicReference<>(
        new HashMap<>());
    private final AtomicReference<Map<String, Configuration>> processedConfigSnapshot = new AtomicReference<>(
        new HashMap<>());

    public Map<String, Configuration> getPrivateConfigsSnapshot() {
        return privateConfigSnapshot.get();
    }

    public Map<String, Configuration> getStorageConfigsSnapshot() {
        return storageConfigSnapshot.get();
    }

    public Map<String, Configuration> getProcessedConfigsSnapshot() {
        return processedConfigSnapshot.get();
    }

    public void syncSnapshots() {
        storageConfigSnapshot.set(storage);
        privateConfigSnapshot.set(privateStorage);
        processedConfigSnapshot.set(processedStorage);
    }

    public void updateStorage(String path, Configuration config) {
        storage.put(path, config);
    }
}
