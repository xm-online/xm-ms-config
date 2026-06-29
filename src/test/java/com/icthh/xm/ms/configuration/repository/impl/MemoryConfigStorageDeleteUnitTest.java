package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.configuration.AbstractUnitTest;
import com.icthh.xm.ms.configuration.config.ApplicationProperties;
import com.icthh.xm.ms.configuration.service.TenantAliasTreeStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the behaviour {@code FileConfigWatcher} relies on: saving a config with empty content
 * removes it from the in-memory storage (this is what makes a deleted file flow through
 * {@code updateConfigurationInMemory} as a removal).
 */
public class MemoryConfigStorageDeleteUnitTest extends AbstractUnitTest {

    private MemoryConfigStorage storage;

    @BeforeEach
    void setUp() {
        TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);
        TenantAliasTreeStorage aliasTreeStorage = new TenantAliasTreeStorage(tenantContextHolder);
        ApplicationProperties applicationProperties = new ApplicationProperties();
        storage = new MemoryConfigStorageImpl(List.of(), aliasTreeStorage, applicationProperties, new ReentrantLock());
    }

    @Test
    void emptyContent_removesConfigFromMemory() {
        String path = "/config/tenants/XM/a.yml";

        storage.saveConfigs(List.of(new Configuration(path, "hello")));
        assertThat(storage.getConfig(path)).isPresent();

        Set<String> changed = storage.saveConfigs(List.of(new Configuration(path, "")));

        assertThat(changed).contains(path);            // reported as changed -> will be notified
        assertThat(storage.getConfig(path)).isEmpty(); // removed from memory
    }
}
