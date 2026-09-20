package me.juancayc.polaroidhomes.config.migration;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * One version bump for one shipped YAML file.
 *
 * <p>A step receives the operator's live file and mutates it in place. It must only touch what
 * actually changed between the two versions: everything it leaves alone is a setting the operator
 * customized, and rewriting it would silently revert their edit.
 *
 * <p>Adding a step is the entire cost of shipping a config change. Implement this interface (or
 * pass a lambda), then register it in {@link ConfigMigrations}. Nothing else in the engine needs
 * to know the step exists.
 */
@FunctionalInterface
public interface MigrationStep {

    /**
     * Rewrites the file from {@code from()} to {@code from() + 1}.
     *
     * @param config the operator's file, already loaded, mutated in place
     */
    void apply(YamlConfiguration config);

    /**
     * Moves a value to a new path, keeping whatever the operator set.
     *
     * <p>The common case for a renamed key: a plain default-merge would add the new path with the
     * shipped default and abandon the operator's value under the dead one.
     */
    static void move(YamlConfiguration config, String from, String to) {
        if (!config.contains(from)) {
            return;
        }
        config.set(to, config.get(from));
        config.set(from, null);
    }

    /** Drops a key that no longer exists, so stale settings do not linger and confuse operators. */
    static void remove(YamlConfiguration config, String path) {
        config.set(path, null);
    }
}
