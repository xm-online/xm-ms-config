package com.icthh.xm.ms.configuration.repository;

public interface PersistenceConfigRepositoryStrategy extends PersistenceConfigRepository {

    /**
     * Returns the priority of this repository.
     * Lower values indicate higher priority (1 = highest priority).
     * Used to determine the order of repository operations and conflict resolution.
     *
     * @return the priority value (1 for highest priority, 2 for second, etc.)
     */
    int priority();

    /**
     * Determines if this repository should handle the given configuration path.
     * Used for routing operations to the appropriate repository.
     *
     * @param path the configuration path to check
     * @return true if this repository should handle the path, false otherwise
     */
    boolean isApplicable(String path);

}
