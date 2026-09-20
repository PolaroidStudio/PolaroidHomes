package me.juancayc.polaroidhomes.config.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * The registry: every shipped YAML, the version this jar ships for it, and its steps.
 *
 * <h2>Shipping a config change</h2>
 * <ol>
 *   <li>Bump the constant for that file below, and the {@code config-version} in the matching
 *       {@code src/main/resources} file so a fresh install writes the new number.</li>
 *   <li>If the change only <em>adds</em> keys, stop. The engine adds absent keys from the jar
 *       automatically, so no step is needed.</li>
 *   <li>If a key moved, was renamed, changed units, or disappeared, register one step for the
 *       version it moved away from. See {@link #exampleRenameStep()} for a worked one.</li>
 * </ol>
 *
 * <p>All three files sit at version 1, the baseline this system was introduced at, and no real
 * step is registered yet. The example below is exercised by the unit tests so the machinery is
 * proven before the first real migration depends on it.
 */
public final class ConfigMigrations {

    /** Baseline. Bump alongside {@code config-version} in the matching resource file. */
    public static final int CONFIG_VERSION = 1;
    public static final int DATA_VERSION = 1;
    public static final int MESSAGES_VERSION = 1;

    private ConfigMigrations() {
    }

    public static FileMigration config() {
        return new FileMigration("config.yml", CONFIG_VERSION);
        // No step yet: version 1 is the first version this engine ever saw. A pre-versioning
        // file reads as version 0, and the engine's add-missing-keys pass is all it needs,
        // since nothing was renamed when config-version was introduced.
    }

    public static FileMigration data() {
        return new FileMigration("data.yml", DATA_VERSION);
    }

    public static FileMigration messages() {
        return new FileMigration("lang/messages_en.yml", MESSAGES_VERSION);
    }

    /**
     * A worked example of a real step, kept because the registry would otherwise ship untested.
     *
     * <p>It models the concrete change this release would have needed if storage settings had been
     * versioned before moving: {@code storage.save-interval-seconds} in {@code config.yml} used to
     * sit under a {@code storage} block that also held the backend {@code type}. The type moved to
     * {@code data.yml}; the interval stayed but no longer belongs under a section whose other keys
     * are gone.
     *
     * <p>This is what every future step looks like: move what the operator set, drop what no
     * longer exists, and touch nothing else.
     */
    public static MigrationStep exampleRenameStep() {
        return config -> {
            MigrationStep.move(config, "storage.save-interval-seconds", "icons.save-interval-seconds");
            MigrationStep.remove(config, "storage");
        };
    }

    /**
     * Runs every registered migration against the operator's data folder.
     *
     * <p>Called once on enable, before anything reads a configuration value. A file that fails to
     * migrate is logged and skipped: the rest of the plugin still starts, and the untouched file
     * plus its backup are both on disk for the operator.
     */
    public static void runAll(Plugin plugin) {
        migrate(plugin, "config.yml", config());
        migrate(plugin, "data.yml", data());
        migrate(plugin, "lang/messages_en.yml", messages());
    }

    private static void migrate(Plugin plugin, String resourcePath, FileMigration migration) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.isFile()) {
            // Never written yet; saveResource will lay down the current version verbatim.
            return;
        }
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return;
            }
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            YamlConfiguration defaults = ConfigMigrator.loadDefaults(reader);
            new ConfigMigrator(plugin.getLogger()).migrate(file, defaults, migration);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not read the bundled " + resourcePath + "; skipped its migration.", ex);
        }
    }
}
