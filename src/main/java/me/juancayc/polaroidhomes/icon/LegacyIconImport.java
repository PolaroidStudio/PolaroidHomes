package me.juancayc.polaroidhomes.icon;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One-time import of the pre-SQL {@code icons.yml} into the database.
 *
 * <p>Runs on enable, before the plugin serves anybody. The source file is renamed rather than
 * deleted: it is user data, and a failed import that also destroyed the original would be
 * unrecoverable. The rename is also what makes this one-time — a file named
 * {@code icons.yml.migrated} is never looked at again.
 */
public final class LegacyIconImport {

    /** Suffix appended to the old file once its rows are safely in SQL. */
    public static final String MIGRATED_SUFFIX = ".migrated";

    private LegacyIconImport() {
    }

    /**
     * Imports {@code icons.yml} if it is still there.
     *
     * <p>Blocking. Called on enable, where the plugin is not yet serving ticks.
     *
     * @return how many icon rows were imported, or 0 when there was nothing to do
     */
    public static int run(File legacyFile, SqlIconStorage storage, Logger logger) {
        if (!legacyFile.isFile()) {
            return 0;
        }

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        int imported = 0;
        for (String rawUuid : legacy.getKeys(false)) {
            UUID player;
            try {
                player = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ex) {
                // A hand-edited or corrupted key is skipped rather than aborting the whole import;
                // the file is kept anyway, so nothing is lost by moving past it.
                logger.warning("Skipping unreadable uuid in icons.yml: " + rawUuid);
                continue;
            }
            ConfigurationSection section = legacy.getConfigurationSection(rawUuid);
            if (section == null) {
                continue;
            }
            // The cache starts empty for this player, so seeding it before writing keeps setIcon
            // from being read back against a database it has not loaded from.
            storage.loadPlayer(player);
            for (String home : section.getKeys(false)) {
                String reference = section.getString(home);
                if (reference == null || reference.isBlank()) {
                    continue;
                }
                storage.setIcon(player, home.toLowerCase(Locale.ROOT), reference);
                imported++;
            }
        }

        // Flushed before the rename: if the write fails, the original file is still in place and
        // the import runs again on the next enable.
        storage.flush();

        File renamed = new File(legacyFile.getParentFile(), legacyFile.getName() + MIGRATED_SUFFIX);
        if (!legacyFile.renameTo(renamed)) {
            logger.log(Level.WARNING, "Imported " + imported + " icon rows but could not rename "
                    + legacyFile.getName() + ". Rename it by hand to stop the import repeating.");
        } else {
            logger.info("Imported " + imported + " icon rows from icons.yml into SQL storage. "
                    + "The original was kept as " + renamed.getName() + ".");
        }
        return imported;
    }
}
