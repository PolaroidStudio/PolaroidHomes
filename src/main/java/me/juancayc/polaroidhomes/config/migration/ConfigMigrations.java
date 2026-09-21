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
    public static final int CONFIG_VERSION = 6;
    public static final int DATA_VERSION = 1;
    public static final int MESSAGES_VERSION = 3;

    /**
     * menu.yml's version.
     *
     * <p>It shipped at version 1 as a new file. Version 2 is its first real migration: the effect
     * menus arrived, and the homes grid gained the button that opens them — see
     * {@link #addEffectsButtonOnUpgrade()}.
     */
    public static final int MENU_VERSION = 2;

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
                .step(4, disableTpaEffectsOnUpgrade())
                // v5 -> v6: the single global effect became a per-player catalog. This is the one
                // step in this file that would destroy working configuration if it did nothing:
                // the keys it reads are about to be deleted, and the values in them are an
                // operator’s own tuned effect. See the method for what is preserved and what an
                // upgrading server ends up with.
                .step(5, globalEffectToCatalog());
    }

    public static FileMigration menu() {
        return new FileMigration("menu.yml", MENU_VERSION)
                // v1 -> v2: the effect menus arrived. The two new sections are added by the
                // engine’s own add-absent-keys pass, which is exactly right for them. The homes
                // grid’s row strings are NOT, because an operator may have rewritten them.
                .step(1, addEffectsButtonOnUpgrade());
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
     * Turns an existing global teleport effect into a catalog entry, then removes the old keys.
     *
     * <h2>What this is protecting</h2>
     *
     * <p>Before version 6 a server had exactly one effect, declared in
     * {@code teleport-effects.entry} and {@code teleport-effects.arrival} and switched between
     * renderers by {@code teleport-effects.mode}. That is a real configuration an operator tuned —
     * a model id, an animation pair, a duration they measured by watching it — and version 6
     * deletes every key it lives in. Dropping those keys without reading them first would silently
     * destroy it, and the operator would find out by opening a config file that no longer mentions
     * the effect their server had been playing for months.
     *
     * <p>So the old block is read first and written back out as a catalog entry, under a generated
     * id, in the category its old {@code mode} names. The model, the animation and the duration are
     * carried across exactly. Nothing is invented: the display name is the id in title case and the
     * icon is a safe vanilla item, because neither existed before and there is nothing to preserve.
     *
     * <h2>What an upgrading server actually ends up with</h2>
     *
     * <p>Their effect, as a purchasable product nobody owns yet. It is in the catalog, it is in the
     * menu, and it plays for any player granted {@code polaroidhomes.animation.legacy_effect} (or
     * {@code polaroidhomes.particle.legacy_effect}). Until that node is granted, nobody sees it —
     * including the players who were seeing it yesterday.
     *
     * <p>That gap is inherent to what was asked for and cannot be migrated away. Auto-granting the
     * node to everyone would defeat the entire feature: the point is that effects are sold, and a
     * catalog whose first entry is free to every player on upgrade is a catalog with a hole in it
     * that the operator did not choose. Giving it to nobody is the only default that does not make
     * a commercial decision on the operator’s behalf, and {@code reportCatalog()} says so loudly
     * in the console at every enable so it is read, not discovered.
     *
     * <p>A {@code mode} of {@code none}, and a block with no model and no particle worth keeping,
     * produce no entry at all: there was nothing playing, so there is nothing to preserve.
     */
    public static MigrationStep globalEffectToCatalog() {
        return config -> {
            String mode = mode(config);
            boolean animation = mode.startsWith("model");
            boolean disabled = mode.equals("none") || mode.equals("off") || mode.equals("disabled");

            String model = string(config, "teleport-effects.entry.model", "");
            String animationId = string(config, "teleport-effects.entry.animation", "");
            String arrivalModel = string(config, "teleport-effects.arrival.model", model);
            String arrivalAnimation =
                    string(config, "teleport-effects.arrival.animation", animationId);

            if (!disabled) {
                if (animation && !model.isBlank() && !animationId.isBlank()) {
                    config.set(ANIMATION_PATH + ".display-name", "<#cd80f7>\u029f\u1d07\u0262\u1d00\u1d04\u028f \u1d07\ua730\ua730\u1d07\u1d04\u1d1b");
                    config.set(ANIMATION_PATH + ".icon", "AMETHYST_SHARD");
                    config.set(ANIMATION_PATH + ".entry.model", model);
                    config.set(ANIMATION_PATH + ".entry.animation", animationId);
                    config.set(ANIMATION_PATH + ".entry.duration",
                            config.getDouble("teleport-effects.entry.duration", 2.0D));
                    config.set(ANIMATION_PATH + ".arrival.model", arrivalModel);
                    config.set(ANIMATION_PATH + ".arrival.animation", arrivalAnimation);
                    config.set(ANIMATION_PATH + ".arrival.duration",
                            config.getDouble("teleport-effects.arrival.duration", 1.5D));
                } else if (!animation) {
                    config.set(PARTICLE_PATH + ".display-name", "<#289bd0>\u029f\u1d07\u0262\u1d00\u1d04\u028f \u1d07\ua730\ua730\u1d07\u1d04\u1d1b");
                    config.set(PARTICLE_PATH + ".icon", "ENDER_PEARL");
                    config.set(PARTICLE_PATH + ".entry.particle",
                            string(config, "teleport-effects.entry.particle", "PORTAL")
                                    .toUpperCase(java.util.Locale.ROOT));
                    // The key is `count` now, not `particle-count`: the category already says these
                    // are particles, so repeating it in every field name was noise.
                    config.set(PARTICLE_PATH + ".entry.count",
                            config.getInt("teleport-effects.entry.particle-count", 120));
                    config.set(PARTICLE_PATH + ".entry.radius",
                            config.getDouble("teleport-effects.entry.particle-radius", 1.0D));
                    config.set(PARTICLE_PATH + ".arrival.particle",
                            string(config, "teleport-effects.arrival.particle", "END_ROD")
                                    .toUpperCase(java.util.Locale.ROOT));
                    config.set(PARTICLE_PATH + ".arrival.count",
                            config.getInt("teleport-effects.arrival.particle-count", 80));
                    config.set(PARTICLE_PATH + ".arrival.radius",
                            config.getDouble("teleport-effects.arrival.particle-radius", 1.0D));
                }
            }

            // The new switches, written explicitly rather than left to the add-absent-keys pass.
            // `homes` matches what this server already did, since the old plugin always decorated
            // home teleports. `tpa` carries across whatever the operator had set under the old
            // block, so an upgrade does not turn a feature on OR off behind them.
            config.set("teleport-effects.homes", true);
            config.set("teleport-effects.tpa",
                    config.getBoolean("teleport-effects.tpa.enabled", false));

            // Only now are the old keys removed \u2014 after everything worth keeping has been copied
            // out of them. Order matters here in a way it does not in any other step in this file.
            MigrationStep.remove(config, "teleport-effects.mode");
            MigrationStep.remove(config, "teleport-effects.fallback");
            MigrationStep.remove(config, "teleport-effects.entry");
            MigrationStep.remove(config, "teleport-effects.arrival");
            // Removed last, and only the two leftover children: the boolean above already read
            // `tpa.enabled`, and `tpa` itself is being rewritten as a boolean by that same set.
            MigrationStep.remove(config, "teleport-effects.tpa.enabled");
            MigrationStep.remove(config, "teleport-effects.tpa.entry");
            MigrationStep.remove(config, "teleport-effects.tpa.arrival");
        };
    }

    /** Where a migrated global animation lands. */
    private static final String ANIMATION_PATH = "teleport-effects.animations.legacy_effect";

    /** Where a migrated global particle effect lands. */
    private static final String PARTICLE_PATH = "teleport-effects.particles.legacy_effect";

    /**
     * Reads a string verbatim, with a default for an absent key.
     *
     * <p>Verbatim matters: a Model Engine model id such as
     * {@code ac_vfx_teleport_charge_purple} carries underscores that mean something to Model
     * Engine, and an earlier draft of this step folded them to hyphens along with the mode. That
     * would have migrated the operator's effect into one naming a model that does not exist —
     * a silent break of exactly the configuration this step exists to protect.
     */
    private static String string(YamlConfiguration config, String path, String fallback) {
        String raw = config.getString(path, fallback);
        return raw == null ? fallback : raw.trim();
    }

    /**
     * Reads the old {@code mode} the way {@code EffectMode.parse} used to, which is what makes
     * {@code model-engine}, {@code Model_Engine} and {@code MODELENGINE} migrate the same way.
     */
    private static String mode(YamlConfiguration config) {
        return string(config, "teleport-effects.mode", "particles")
                .toLowerCase(java.util.Locale.ROOT)
                .replace('_', '-');
    }

    /**
     * Adds the effects button to a menu.yml that predates it, without touching the rest.
     *
     * <p>The two new sections are left to the engine\u2019s add-absent-keys pass, which is right for
     * them: an upgrading file has no opinion about a screen that did not exist. The homes grid is
     * different, because the button has to go in a row string the operator may have rewritten, and
     * there is no safe place to put a character in a layout this code did not author.
     *
     * <p>So the row strings are only touched when they are still EXACTLY the shipped v1 layout. An
     * operator who customised their grid keeps it untouched and adds the character themselves; the
     * console line at enable tells them the catalog exists, and menu.yml documents the element. The
     * alternative \u2014 hunting for a filler slot to overwrite \u2014 would move somebody\u2019s deliberately
     * placed button on a release they upgraded for something else.
     */
    public static MigrationStep addEffectsButtonOnUpgrade() {
        return config -> {
            java.util.List<String> rows = config.getStringList("homes.rows");
            if (!SHIPPED_V1_HOMES_ROWS.equals(rows)) {
                return;
            }
            java.util.List<String> updated = new java.util.ArrayList<>(rows);
            updated.set(updated.size() - 1, "<##EI###>");
            config.set("homes.rows", updated);
            // The element declaration itself comes from the add-absent-keys pass, since
            // `homes.elements.E` is a key this file does not have yet.
        };
    }

    /** The exact v1 homes layout, which is the only one this migration will rewrite. */
    private static final java.util.List<String> SHIPPED_V1_HOMES_ROWS = java.util.List.of(
            "HHHHHHHHH",
            "HHHHHHHHH",
            "HHHHHHHHH",
            "HHHHHHHHH",
            "HHHHHHHHH",
            "<###I###>");

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
