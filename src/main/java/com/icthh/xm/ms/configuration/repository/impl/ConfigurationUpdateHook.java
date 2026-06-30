package com.icthh.xm.ms.configuration.repository.impl;

import com.icthh.xm.commons.config.domain.Configuration;

import java.util.List;

/**
 * Extension point applied to the batch of configurations about to be processed by
 * {@link MemoryConfigStorage}'s {@code saveConfigs} — the single chokepoint every config mutation
 * passes through (update, in-memory update, full / partial / tenant refresh, delete).
 *
 * <p>Implementations may append ADDITIONAL configurations that must be re-processed (and therefore
 * re-derived and notified) together with the originally changed ones.
 * Contributing them here, before processing, lets them flow
 * through the normal tenant-processor pass; appending paths after processing would be too late, since
 * derived configurations would never be recomputed.
 *
 * <p>The default behaviour (no implementations registered) leaves the batch unchanged.
 */
public interface ConfigurationUpdateHook {

    /**
     * @param configurations the configurations about to be processed in memory
     * @param storage        the storage being updated, for looking up dependent configurations
     * @return the collection to actually process — the input, optionally extended with dependents
     */
    List<Configuration> beforeProcess(List<Configuration> configurations, MemoryConfigStorage storage);
}
