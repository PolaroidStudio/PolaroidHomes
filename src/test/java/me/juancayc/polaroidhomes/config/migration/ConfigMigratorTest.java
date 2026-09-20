package me.juancayc.polaroidhomes.config.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the migration machinery before any real migration depends on it.
 *
 * <p>Every case here is one of the guarantees the engine claims: operator edits survive, new keys
 * arrive, a step's rename keeps the operator's value, a backup exists, and a downgraded file is
 * refused rather than mangled.
 */
class ConfigMigratorTest {

    @TempDir
    Path folder;

    private final Logger logger = Logger.getLogger("migration-test");

    private File write(String name, String content) throws IOException {
        File file = folder.resolve(name).toFile();
        Files.writeString(file.toPath(), content);
        return file;
    }

    private YamlConfiguration defaults(String content) {
        return YamlConfiguration.loadConfiguration(new java.io.StringReader(content));
    }

    @Test
    void anUpToDateFileIsLeftAlone() throws IOException {
        File file = write("config.yml", "config-version: 1\ngui:\n  rows: 3\n");
        long before = file.lastModified();

        ConfigMigrator.Result result = new ConfigMigrator(logger).migrate(
                file, defaults("config-version: 1\ngui:\n  rows: 6\n"),
                new FileMigration("config.yml", 1));

        assertEquals(ConfigMigrator.Result.UP_TO_DATE, result);
        assertEquals(before, file.lastModified());
        assertEquals(3, YamlConfiguration.loadConfiguration(file).getInt("gui.rows"),
                "an up-to-date file must not have its values replaced by the jar's");
    }

    @Test
    void aVersionlessFileIsTreatedAsVersionZeroAndMigratedForward() throws IOException {
        File file = write("config.yml", "gui:\n  rows: 3\n");

        ConfigMigrator.Result result = new ConfigMigrator(logger).migrate(
                file, defaults("config-version: 1\ngui:\n  rows: 6\n"),
                new FileMigration("config.yml", 1));

        assertEquals(ConfigMigrator.Result.MIGRATED, result);
        assertEquals(1, YamlConfiguration.loadConfiguration(file).getInt("config-version"));
    }

    @Test
    void operatorEditsSurviveAndNewKeysAreAdded() throws IOException {
        File file = write("config.yml", "config-version: 0\ngui:\n  rows: 3\n");

        new ConfigMigrator(logger).migrate(
                file,
                defaults("config-version: 1\ngui:\n  rows: 6\n  filler-icon: BLACK_STAINED_GLASS_PANE\n"
                        + "language: en\n"),
                new FileMigration("config.yml", 1));

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals(3, migrated.getInt("gui.rows"), "the operator's value must not be reverted");
        assertEquals("BLACK_STAINED_GLASS_PANE", migrated.getString("gui.filler-icon"),
                "a key added by the new version must arrive with its default");
        assertEquals("en", migrated.getString("language"),
                "a new top-level key must be added too");
    }

    @Test
    void aWholeNewSectionIsAddedWithItsNestedDefaults() throws IOException {
        File file = write("config.yml", "config-version: 0\ngui:\n  rows: 3\n");

        new ConfigMigrator(logger).migrate(
                file,
                defaults("config-version: 1\ngui:\n  rows: 6\nicons:\n  save-interval-seconds: 120\n"),
                new FileMigration("config.yml", 1));

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals(120, migrated.getInt("icons.save-interval-seconds"));
    }

    @Test
    void aRegisteredStepMovesTheOperatorsValueRatherThanTheDefault() throws IOException {
        // The worked example from the registry: storage.save-interval-seconds became
        // icons.save-interval-seconds when the backend settings left config.yml.
        File file = write("config.yml",
                "config-version: 0\nstorage:\n  type: yaml\n  save-interval-seconds: 45\n");

        ConfigMigrator.Result result = new ConfigMigrator(logger).migrate(
                file,
                defaults("config-version: 1\nicons:\n  save-interval-seconds: 120\n"),
                new FileMigration("config.yml", 1).step(0, ConfigMigrations.exampleRenameStep()));

        assertEquals(ConfigMigrator.Result.MIGRATED, result);
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals(45, migrated.getInt("icons.save-interval-seconds"),
                "the operator's 45 must move to the new path, not be replaced by the default 120");
        assertFalse(migrated.contains("storage"),
                "the dead section must be dropped so it cannot confuse the operator");
    }

    @Test
    void severalStepsRunInOrderAcrossMultipleVersions() throws IOException {
        File file = write("config.yml", "config-version: 0\na: 7\n");

        FileMigration migration = new FileMigration("config.yml", 3)
                .step(0, config -> MigrationStep.move(config, "a", "b"))
                .step(1, config -> MigrationStep.move(config, "b", "c"))
                .step(2, config -> MigrationStep.move(config, "c", "d"));

        new ConfigMigrator(logger).migrate(file, defaults("config-version: 3\nd: 1\n"), migration);

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals(7, migrated.getInt("d"), "the value must be carried through every step");
        assertEquals(3, migrated.getInt("config-version"));
    }

    @Test
    void aMissingStepForAVersionIsNotAnError() throws IOException {
        // An unambiguous string value: YAML 1.1 resolves a bare `yes` to a boolean, which would
        // make the assertion about the parser rather than about preservation.
        File file = write("config.yml", "config-version: 0\nkept: custom-value\n");

        // Only the 1 -> 2 bump restructures anything; 0 -> 1 added keys only.
        FileMigration migration = new FileMigration("config.yml", 2)
                .step(1, config -> config.set("added-by-step", true));

        new ConfigMigrator(logger).migrate(file, defaults("config-version: 2\n"), migration);

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertTrue(migrated.getBoolean("added-by-step"));
        assertEquals("custom-value", migrated.getString("kept"));
    }

    @Test
    void theOriginalIsBackedUpBeforeMigrating() throws IOException {
        File file = write("config.yml", "config-version: 0\ngui:\n  rows: 3\n");

        new ConfigMigrator(logger).migrate(
                file, defaults("config-version: 1\ngui:\n  rows: 6\n"),
                new FileMigration("config.yml", 1));

        File backup = folder.resolve("config.yml.bak-v0").toFile();
        assertTrue(backup.isFile(), "a bad migration has to be recoverable");
        assertTrue(Files.readString(backup.toPath()).contains("rows: 3"),
                "the backup must hold the pre-migration content");
    }

    @Test
    void aFileNewerThanTheJarIsRefusedAndLeftUntouched() throws IOException {
        String original = "config-version: 5\nfuture-key: kept\n";
        File file = write("config.yml", original);

        ConfigMigrator.Result result = new ConfigMigrator(logger).migrate(
                file, defaults("config-version: 1\n"), new FileMigration("config.yml", 1));

        assertEquals(ConfigMigrator.Result.REFUSED_DOWNGRADE, result);
        assertEquals(original, Files.readString(file.toPath()),
                "an operator who downgraded the jar must not lose settings the newer one wrote");
        assertFalse(folder.resolve("config.yml.bak-v5").toFile().exists(),
                "nothing was changed, so nothing needed backing up");
    }

    @Test
    void aMissingFileIsNotAnError() {
        ConfigMigrator.Result result = new ConfigMigrator(logger).migrate(
                folder.resolve("absent.yml").toFile(), defaults("config-version: 1\n"),
                new FileMigration("absent.yml", 1));

        assertEquals(ConfigMigrator.Result.UP_TO_DATE, result);
    }

    @Test
    void shippedCommentsAreRestoredOntoTheMigratedFile() throws IOException {
        File file = write("config.yml", "config-version: 0\ngui:\n  rows: 3\n");

        new ConfigMigrator(logger).migrate(
                file,
                defaults("config-version: 1\n# Menu settings.\ngui:\n  # Rows, 1-6.\n  rows: 6\n"),
                new FileMigration("config.yml", 1));

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        // Paper exposes setComments/getComments, so the jar's documentation survives a rewrite.
        assertEquals(List.of("Menu settings."), migrated.getComments("gui"));
        assertEquals(List.of("Rows, 1-6."), migrated.getComments("gui.rows"));
    }

    @Test
    void theShippedRegistryTargetsTheVersionThisJarShips() {
        // config.yml is at 2: max-displayed-slots moved into menu.yml and gui.rows became the row
        // strings there. The other files never needed restructuring, so they stay at the baseline.
        assertEquals(2, ConfigMigrations.config().targetVersion());
        assertEquals(1, ConfigMigrations.menu().targetVersion());
        assertEquals(1, ConfigMigrations.data().targetVersion());
        assertEquals(1, ConfigMigrations.messages().targetVersion());
    }

    @Test
    void theConfigStepDropsTheKeysThatMovedIntoMenuYml() {
        YamlConfiguration config = defaults("""
                config-version: 1
                gui:
                  rows: 3
                  max-displayed-slots: 27
                  default-icon: DIRT
                """);

        ConfigMigrations.dropMovedMaxDisplayedSlots().apply(config);

        assertFalse(config.contains("gui.max-displayed-slots"),
                "the moved key must not linger as a setting that quietly stopped working");
        assertFalse(config.contains("gui.rows"));
        assertEquals("DIRT", config.getString("gui.default-icon"),
                "the step must touch nothing the operator still owns");
    }
}
