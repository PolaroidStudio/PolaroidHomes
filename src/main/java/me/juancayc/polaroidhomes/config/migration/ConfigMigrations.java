package me.juancayc.polaroidhomes.config.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
 * <p>config.yml is at version 2: {@code gui.max-displayed-slots} moved into menu.yml, which is
 * where the layout that gives the cap its meaning now lives. The other files sit at version 1, the
 * baseline this system was introduced at.
 */
public final class ConfigMigrations {

    /** Baseline. Bump alongside {@code config-version} in the matching resource file. */
    public static final int CONFIG_VERSION = 2;
    public static final int DATA_VERSION = 1;
    public static final int MESSAGES_VERSION = 1;

    /**
     * menu.yml's baseline.
     *
     * <p>It ships at version 1 because it is a new file: an operator upgrading into this release has
     * no menu.yml at all until saveResource writes one, and that copy already carries version 1.
     * What does need moving is {@code gui.max-displayed-slots}, which used to live in config.yml —
     * see {@link #config()} and {@link #maxDisplayedSlotsToMenu(Plugin)}.
     */
    public static final int MENU_VERSION = 1;

    private ConfigMigrations() {
    }

    public static FileMigration config() {
        return new FileMigration("config.yml", CONFIG_VERSION)
                // gui.max-displayed-slots moved to menu.yml, where the layout that gives the cap
                // its meaning now lives. The value itself is carried across by
                // maxDisplayedSlotsToMenu before this step drops the dead key, so an operator who
                // raised or lowered it keeps their number. gui.rows is dropped outright: the row
                // strings in menu.yml are the row count now.
                .step(1, dropMovedMaxDisplayedSlots());
    }

    public static FileMigration menu() {
        return new FileMigration("menu.yml", MENU_VERSION);
    }

    /** Removes the key {@link #maxDisplayedSlotsToMenu} has already copied into menu.yml. */
    public static MigrationStep dropMovedMaxDisplayedSlots() {
        return config -> {
            MigrationStep.remove(config, "gui.max-displayed-slots");
            // gui.rows is gone rather than moved: menu.yml's row strings are the row count now, and
            // there is no honest way to translate a number into a layout the operator would
            // recognise as theirs. Left in place it would read as a setting that quietly stopped
            // working.
            MigrationStep.remove(config, "gui.rows");
        };
    }

    /**
     * Copies a pre-existing {@code gui.max-displayed-slots} from config.yml into menu.yml.
     *
     * <p>A cross-file move is not something a {@link MigrationStep} can express — a step only sees
     * the one file it is migrating — so it runs here, before either file's own migration. It writes
     * only when config.yml still has the key and menu.yml has not been given a value of its own, so
     * a second enable and an operator who has since edited menu.yml are both left alone.
     */
    static void maxDisplayedSlotsToMenu(Plugin plugin) {
        File source = new File(plugin.getDataFolder(), "config.yml");
        File target = new File(plugin.getDataFolder(), "menu.yml");
        if (!source.isFile() || !target.isFile()) {
            return;
        }
        YamlConfiguration from = YamlConfiguration.loadConfiguration(source);
        if (!from.contains("gui.max-displayed-slots")) {
            return;
        }
        YamlConfiguration to = YamlConfiguration.loadConfiguration(target);
        int shipped = to.getInt("max-displayed-slots", 45);
        int configured = from.getInt("gui.max-displayed-slots", shipped);
        if (configured == shipped) {
            return;
        }
        to.set("max-displayed-slots", configured);
        try {
            to.save(target);
            plugin.getLogger().info("Moved gui.max-displayed-slots (" + configured
                    + ") from config.yml into menu.yml, where the layout it caps now lives.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not move gui.max-displayed-slots into "
                    + "menu.yml. Copy the value across by hand; the default is being used until "
                    + "then.", ex);
        }
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
        // Before config.yml's own step, which is the one that deletes the old key.
        maxDisplayedSlotsToMenu(plugin);
        migrate(plugin, "config.yml", config());
        migrate(plugin, "menu.yml", menu());
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
