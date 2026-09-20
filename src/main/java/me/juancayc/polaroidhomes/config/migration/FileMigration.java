package me.juancayc.polaroidhomes.config.migration;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The migration steps registered for one shipped YAML file.
 *
 * <p>A step is keyed by the version it migrates <em>from</em>, so the engine can walk
 * {@code fileVersion -> targetVersion} one bump at a time without knowing anything about the
 * file's shape. A missing step for a version is not an error: it simply means that bump needed no
 * restructuring, and the engine's own "add keys the jar ships" pass covers it.
 *
 * <p>Extending this is one call per release. Nothing dispatches on version numbers, so no switch
 * grows over time.
 */
public final class FileMigration {

    private final String name;
    private final int targetVersion;
    private final Map<Integer, MigrationStep> steps = new HashMap<>();

    /**
     * @param name          the file this describes, for logging
     * @param targetVersion the version the current jar ships
     */
    public FileMigration(String name, int targetVersion) {
        this.name = name;
        this.targetVersion = targetVersion;
    }

    /**
     * Registers the step that turns version {@code fromVersion} into {@code fromVersion + 1}.
     *
     * @return this, so registrations chain
     */
    public FileMigration step(int fromVersion, MigrationStep step) {
        steps.put(fromVersion, step);
        return this;
    }

    public @Nullable MigrationStep step(int fromVersion) {
        return steps.get(fromVersion);
    }

    public int targetVersion() {
        return targetVersion;
    }

    public String name() {
        return name;
    }
}
