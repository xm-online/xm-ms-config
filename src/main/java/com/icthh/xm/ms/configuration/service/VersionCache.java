package com.icthh.xm.ms.configuration.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.domain.ConfigVersion;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class VersionCache {
    private final Cache<ConfigVersion, Boolean> cache;
    private final AtomicReference<ConfigVersion> lastVersion = new AtomicReference<>(ConfigVersion.UNDEFINED_VERSION);

    public VersionCache(ApplicationProperties applicationProperties) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(applicationProperties.getVersionCacheMaxSize())
                .build();
    }

    public void addVersion(ConfigVersion version) {
        cache.put(version, Boolean.TRUE);
        lastVersion.set(version);
    }

    public boolean containsVersion(ConfigVersion version) {
        return cache.getIfPresent(version) != null;
    }

    public ConfigVersion getLastVersion() {
        return lastVersion.get();
    }
}
