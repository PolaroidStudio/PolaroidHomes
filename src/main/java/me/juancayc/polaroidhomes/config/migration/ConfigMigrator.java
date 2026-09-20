package me.juancayc.polaroidhomes.config.migration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Brings one shipped YAML file up to the version the jar expects, without losing operator edits.
 *
 * <h2>How a file is migrated</h2>
 * <ol>
 *   <li>Read {@code config-version} from the operator's file. A file without the key is treated as
 *       version 0, which is what every file written before this system existed is.</li>
 *   <li>If it already matches the jar, nothing happens — not even a rewrite, so an untouched file
 *       keeps its modification time and its formatting.</li>
 *   <li>If it is <em>newer</em> than the jar, the plugin refuses to touch it and warns. Running a
 *       downgrade's steps backwards is not possible, and merging the older jar's defaults into a
 *       newer file would delete settings that release does not know about yet.</li>
 *   <li>Otherwise the file is copied to {@code <name>.yml.bak-v<old>}, the registered steps run in
 *       order, keys the jar ships but the file lacks are added with their defaults, and the result
 *       is saved with the new version.</li>
 * </ol>
 *
 * <h2>Comments</h2>
 * <p>Bukkit's {@code YamlConfiguration} drops comments when it reloads and re-saves a file, which
 * would strip every explanatory block out of a migrated {@code config.yml}. Paper's configuration
 * API exposes {@code getComments}/{@code setComments} (and their inline variants), so this class
 * re-applies the jar's comments onto every path after migrating. The honest limitation: comments
 * the <em>operator</em> wrote themselves are not recoverable — only the shipped ones come back,
 * because only the shipped file is available to copy them from. That is why the pre-migration
 * backup is written unconditionally.
 */
public final class ConfigMigrator {

    /** The key every shipped YAML carries. Absent means version 0. */
    public static final String VERSION_KEY = "config-version";

    private final Logger logger;

    public ConfigMigrator(Logger logger) {
        this.logger = logger;
    }

    /** What a migration attempt did, so callers (and tests) can assert on it. */
    public enum Result {
        /** The file was already at the jar's version. */
        UP_TO_DATE,
        /** Steps ran and the file was rewritten. */
        MIGRATED,
        /** The file is newer than the jar; nothing was touched. */
        REFUSED_DOWNGRADE,
        /** Reading or writing failed; nothing usable was written. */
        FAILED
    }

    /**
     * Migrates {@code file} in place.
     *
     * @param file      the operator's file on disk; missing files are left to the caller's
     *                  saveResource path and reported as {@link Result#UP_TO_DATE}
     * @param defaults  the jar's copy of the same file, used for new keys and for comments
     * @param migration the registered steps and target version for this file
     */
    public Result migrate(File file, YamlConfiguration defaults, FileMigration migration) {
        if (!file.isFile()) {
            return Result.UP_TO_DATE;
        }

        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        int fileVersion = current.getInt(VERSION_KEY, 0);
        int jarVersion = migration.targetVersion();

        if (fileVersion == jarVersion) {
            return Result.UP_TO_DATE;
        }
        if (fileVersion > jarVersion) {
            logger.warning(file.getName() + " is at config-version " + fileVersion
                    + " but this build of PolaroidHomes only understands version " + jarVersion
                    + ". The file was left untouched. Restore the newer plugin jar, or downgrade "
                    + "the file by hand.");
            return Result.REFUSED_DOWNGRADE;
        }

        try {
            backup(file, fileVersion);
        } catch (IOException ex) {
            // Without a recoverable copy the migration is not worth its risk, so it does not run.
            logger.log(Level.SEVERE, "Could not back up " + file.getName()
                    + " before migrating. The file was left untouched.", ex);
            return Result.FAILED;
        }

        for (int version = fileVersion; version < jarVersion; version++) {
            MigrationStep step = migration.step(version);
            if (step != null) {
                step.apply(current);
            }
        }

        addMissingKeys(current, defaults);
        current.set(VERSION_KEY, jarVersion);
        copyComments(current, defaults);

        try {
            current.save(file);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not save the migrated " + file.getName()
                    + ". The pre-migration backup is still on disk.", ex);
            return Result.FAILED;
        }

        logger.info("Migrated " + file.getName() + " from config-version " + fileVersion
                + " to " + jarVersion + ".");
        return Result.MIGRATED;
    }

    private static void backup(File file, int fileVersion) throws IOException {
        File backup = new File(file.getParentFile(), file.getName() + ".bak-v" + fileVersion);
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Adds keys the jar ships and the file lacks, and nothing else.
     *
     * <p>Deliberately not {@code options().copyDefaults(true)}: that would also re-add every key a
     * step just deleted on purpose, because the jar's older shape is not what the defaults hold.
     * Walking the jar's tree and only writing absent paths leaves every existing value alone,
     * whether the operator set it or a step did.
     */
    private static void addMissingKeys(ConfigurationSection target, ConfigurationSection defaults) {
        for (Map.Entry<String, Object> entry : defaults.getValues(false).entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof ConfigurationSection section) {
                if (target.isConfigurationSection(key)) {
                    addMissingKeys(target.getConfigurationSection(key), section);
                } else if (!target.contains(key)) {
                    target.createSection(key, flatten(section));
                }
            } else if (!target.contains(key)) {
                target.set(key, value);
            }
        }
    }

    /** A nested section has to be handed to createSection as plain maps, not as section objects. */
    private static Map<String, Object> flatten(ConfigurationSection section) {
        Map<String, Object> values = section.getValues(false);
        values.replaceAll((key, value) ->
                value instanceof ConfigurationSection nested ? flatten(nested) : value);
        return values;
    }

    /**
     * Re-applies the jar's comments onto the migrated file.
     *
     * <p>Only paths the jar knows about get comments back; an operator's own added keys keep no
     * comment, because there is no source to take one from.
     */
    private static void copyComments(ConfigurationSection target, ConfigurationSection defaults) {
        for (String path : defaults.getKeys(true)) {
            if (!target.contains(path)) {
                continue;
            }
            List<String> comments = defaults.getComments(path);
            if (!comments.isEmpty()) {
                target.setComments(path, comments);
            }
            List<String> inline = defaults.getInlineComments(path);
            if (!inline.isEmpty()) {
                target.setInlineComments(path, inline);
            }
        }
    }

    /** Loads the jar's copy of a resource. Used for both the new keys and the comments. */
    public static YamlConfiguration loadDefaults(Reader reader) {
        return YamlConfiguration.loadConfiguration(reader);
    }
}
