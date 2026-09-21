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
 * <p>config.yml is at version 5. Version 2 moved {@code gui.max-displayed-slots} into menu.yml,
 * where the layout that gives the cap its meaning now lives; version 3 added the {@code hooks}
 * section, which names the home provider; version 4 added command interception and the world
 * blacklist; version 5 added teleport-request effects. The other files sit at version 1, the
 * baseline this system was introduced at.
 */
public final class ConfigMigrations {

    /** Baseline. Bump alongside {@code config-version} in the matching resource file. */
    public static final int CONFIG_VERSION = 5;
    public static final int DATA_VERSION = 1;
    public static final int MESSAGES_VERSION = 2;

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
                .step(1, dropMovedMaxDisplayedSlots())
                // v2 -> v3: the hooks section arrived. Written explicitly rather than left to the
                // engine's add-absent-keys pass because the default this existing install needs is
                // NOT the shipped one: it has been running on EssentialsX, and 'auto' would keep
                // choosing EssentialsX today but would silently change backend the day the operator
                // installs HuskHomes. Pinning the backend the icons were recorded against is the
                // only answer that cannot move under them.
                .step(2, pinExistingInstallToEssentials())
                // v3 -> v4: command interception and the world blacklist arrived. Both are written
                // explicitly for the same reason v3's step was: the engine's add-absent-keys pass
                // would give this install the SHIPPED defaults, and for interception the shipped
                // default turns a feature on. An existing server's players have been typing /homes
                // and getting their backend's answer for as long as the plugin has been installed;
                // changing what that command does during an upgrade they did not read about is not
                // a migration, it is a surprise. So an upgrading file gets interception off, and a
                // fresh install gets the shipped 'homes: true' from the file itself.
                .step(3, disableInterceptionOnUpgrade())
                // v4 -> v5: teleport effects extended to accepted /tpa requests. Same judgement as
                // v3's step, and for the same reason: the shipped default is on, and letting the
                // add-absent-keys pass hand that default to an existing install would make a
                // cosmetic upgrade change what every player on the server sees the next time
                // somebody accepts a tpa. Nobody asked for that during an upgrade, so it arrives
                // off and the operator turns it on when they have read what it does. A fresh
                // install gets 'enabled: true' from the shipped file.
                .step(4, disableTpaEffectsOnUpgrade());
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
     * Writes {@code hooks.home-provider: essentialsx} into a config.yml that predates the key.
     *
     * <p>A file being migrated from version 2 came from a build where EssentialsX was a hard
     * dependency, so EssentialsX is provably the backend its stored icons are keyed against. A fresh
     * install gets {@code auto} from the shipped file instead, because it has no history to preserve.
     */
    public static MigrationStep pinExistingInstallToEssentials() {
        return config -> {
            if (!config.contains("hooks.home-provider")) {
                config.set("hooks.home-provider", "essentialsx");
            }
        };
    }

    /**
     * Writes {@code commands.intercept} off into a config.yml that predates the section.
     *
     * <p>{@code worlds.blacklist} is deliberately NOT written here: the engine's add-absent-keys
     * pass gives it the shipped empty list, and an empty blacklist blocks nothing, so the shipped
     * default and the correct upgrade value are the same thing. Interception is the opposite case —
     * its shipped default is on — which is the whole reason this step exists.
     */
    public static MigrationStep disableInterceptionOnUpgrade() {
        return config -> {
            if (!config.contains("commands.intercept.homes")) {
                config.set("commands.intercept.homes", false);
            }
            if (!config.contains("commands.intercept.home")) {
                config.set("commands.intercept.home", false);
            }
        };
    }

    /**
     * Writes {@code teleport-effects.tpa.enabled: false} into a config.yml that predates the key.
     *
     * <p>The rest of the {@code tpa} block is deliberately NOT written here. Every other key in it
     * is optional by design — an unset field inherits from the home effect — so the engine's
     * add-absent-keys pass giving this install the shipped {@code particle} lines is exactly right:
     * those are what a fresh install would get too, and the feature they configure is switched off
     * until the operator says otherwise. Only {@code enabled} has a shipped default that would
     * change behaviour, which is the whole reason this step exists.
     */
    public static MigrationStep disableTpaEffectsOnUpgrade() {
        return config -> {
            if (!config.contains("teleport-effects.tpa.enabled")) {
                config.set("teleport-effects.tpa.enabled", false);
            }
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
