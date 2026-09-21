package me.juancayc.polaroidhomes.icon;

import me.juancayc.polaroidhomes.storage.DatabaseManager;
import me.juancayc.polaroidhomes.storage.StorageSettings;
import me.juancayc.polaroidhomes.storage.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the storage against a real temp-file SQLite database rather than a fake.
 *
 * <p>The behaviour worth testing here is the boundary between the cache and the table — dirty
 * tracking, the composite key, the upsert dialect — and a stubbed connection would assert on the
 * test's own idea of SQL instead of on what SQLite actually accepts.
 */
class SqlIconStorageTest {

    @TempDir
    Path folder;

    private DatabaseManager database;
    private SqlIconStorage storage;

    private final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private final UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

    @BeforeEach
    void open() {
        this.database = openDatabase();
        this.storage = new SqlIconStorage(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private DatabaseManager openDatabase() {
        StorageSettings settings = new StorageSettings(
                StorageType.SQLITE, "homes.db", "", 0, "", "", "", 1);
        return new DatabaseManager(settings, folder.toFile());
    }

    /** Reopens the same file through a new manager, proving a value survived the process. */
    private SqlIconStorage reopen() {
        database.close();
        this.database = openDatabase();
        return new SqlIconStorage(database);
    }

    @Test
    void sqliteFileLivesUnderTheDataFolder() {
        assertTrue(new File(folder.toFile(), "data/homes.db").isFile(),
                "the database must be created at data/homes.db, never beside the YAML configs");
    }

    @Test
    void setAndGetRoundTripThroughTheDatabase() {
        storage.loadPlayer(alice);
        storage.setIcon(alice, "base", "DIAMOND_BLOCK");
        storage.flush();

        SqlIconStorage reopened = reopen();
        reopened.loadPlayer(alice);
        assertEquals("DIAMOND_BLOCK", reopened.icon(alice, "base"));
    }

    @Test
    void homeNamesAreCaseInsensitive() {
        storage.loadPlayer(alice);
        storage.setIcon(alice, "Base", "CHEST");
        storage.flush();

        SqlIconStorage reopened = reopen();
        reopened.loadPlayer(alice);
        // Every supported provider treats home names case-insensitively, so /sethome Base and
        // /home base are one home and must share one icon.
        assertEquals("CHEST", reopened.icon(alice, "base"));
        assertEquals("CHEST", reopened.icon(alice, "BASE"));
    }

    @Test
    void theCompositeKeySeparatesPlayersAndHomes() {
        storage.loadPlayer(alice);
        storage.loadPlayer(bob);
        storage.setIcon(alice, "base", "CHEST");
        storage.setIcon(bob, "base", "ANVIL");
        storage.setIcon(alice, "mine", "FURNACE");
        storage.flush();

        SqlIconStorage reopened = reopen();
        reopened.loadPlayer(alice);
        reopened.loadPlayer(bob);
        assertEquals("CHEST", reopened.icon(alice, "base"));
        assertEquals("ANVIL", reopened.icon(bob, "base"));
        assertEquals("FURNACE", reopened.icon(alice, "mine"));
    }

    @Test
    void settingTheSameKeyTwiceUpsertsInsteadOfDuplicating() {
        storage.loadPlayer(alice);
        storage.setIcon(alice, "base", "CHEST");
        storage.flush();
        storage.setIcon(alice, "base", "BEACON");
        storage.flush();

        SqlIconStorage reopened = reopen();
        reopened.loadPlayer(alice);
        assertEquals("BEACON", reopened.icon(alice, "base"),
                "a second write to the same composite key must replace the row, not add one");
    }

    @Test
    void clearingAnIconDeletesTheStoredRow() {
        storage.loadPlayer(alice);
        storage.setIcon(alice, "base", "CHEST");
        storage.flush();

        storage.setIcon(alice, "base", null);
        storage.flush();

        SqlIconStorage reopened = reopen();
        reopened.loadPlayer(alice);
        assertNull(reopened.icon(alice, "base"));
    }

    @Test
    void renameMovesTheIconAndLeavesNoOrphan() {
        storage.loadPlayer(alice);
        storage.setIcon(alice, "base", "CHEST");
        storage.flush();

        storage.renameHome(alice, "Base", "Cabin");
        storage.flush();

        SqlIconStorage reopened = reopen();
        reopened.loadPlayer(alice);
        assertEquals("CHEST", reopened.icon(alice, "cabin"));
        assertNull(reopened.icon(alice, "base"), "the source row must be deleted, not left behind");
    }

    @Test
    void deleteRemovesTheRow() {
        storage.loadPlayer(alice);
        storage.setIcon(alice, "base", "CHEST");
        storage.flush();

        storage.deleteHome(alice, "BASE");
        storage.flush();

        SqlIconStorage reopened = reopen();
        reopened.loadPlayer(alice);
        assertNull(reopened.icon(alice, "base"));
    }

    @Test
    void flushClearsTheDirtySetAndIsANoOpWhenNothingChanged() {
        storage.loadPlayer(alice);
        storage.setIcon(alice, "base", "CHEST");
        assertEquals(1, storage.pendingWrites());

        storage.flush();
        assertEquals(0, storage.pendingWrites());

        // A second flush with nothing pending must not reach the database at all.
        storage.flush();
        assertEquals(0, storage.pendingWrites());
    }

    @Test
    void readsNeverTouchTheDatabaseForAnUnloadedPlayer() {
        // No loadPlayer call: an uncached read answers null rather than blocking on JDBC, which is
        // what keeps IconStorage callable from the main thread.
        assertNull(storage.icon(bob, "base"));
        assertFalse(storage.isLoaded(bob));
    }

    @Test
    void unloadRefusesToDropRowsThatStillOweAWrite() {
        storage.loadPlayer(alice);
        storage.setIcon(alice, "base", "CHEST");

        storage.unloadPlayer(alice);
        assertEquals("CHEST", storage.icon(alice, "base"),
                "dropping an unflushed pick would lose it silently");

        storage.flush();
        storage.unloadPlayer(alice);
        assertFalse(storage.isLoaded(alice));
    }

    @Test
    void loadDoesNotOverwriteAnUnflushedPick() {
        storage.loadPlayer(alice);
        storage.setIcon(alice, "base", "CHEST");
        storage.flush();

        storage.setIcon(alice, "base", "BEACON");
        storage.loadPlayer(alice);

        assertEquals("BEACON", storage.icon(alice, "base"),
                "the pending pick is newer than the stored row and must win");
    }

    @Test
    void legacyYamlIsImportedAndTheFileIsRenamed() throws Exception {
        File legacy = new File(folder.toFile(), "icons.yml");
        java.nio.file.Files.writeString(legacy.toPath(),
                alice + ":\n  base: CHEST\n  Mine: FURNACE\n"
                        + bob + ":\n  base: ANVIL\n");

        int imported = LegacyIconImport.run(legacy, storage,
                java.util.logging.Logger.getLogger("test"));

        assertEquals(3, imported);
        assertFalse(legacy.exists(), "the original must be renamed, not left to import again");
        File migrated = new File(folder.toFile(), "icons.yml" + LegacyIconImport.MIGRATED_SUFFIX);
        assertTrue(migrated.isFile(), "the operator's data must be kept, not deleted");

        SqlIconStorage reopened = reopen();
        reopened.loadPlayer(alice);
        reopened.loadPlayer(bob);
        assertEquals("CHEST", reopened.icon(alice, "base"));
        assertEquals("FURNACE", reopened.icon(alice, "mine"));
        assertEquals("ANVIL", reopened.icon(bob, "base"));
    }

    @Test
    void importIsASilentNoOpWhenThereIsNoLegacyFile() {
        assertEquals(0, LegacyIconImport.run(new File(folder.toFile(), "icons.yml"), storage,
                java.util.logging.Logger.getLogger("test")));
    }

    @Test
    void theStorageIsUsableThroughTheBackendAgnosticInterface() {
        // The GUI only ever sees IconStorage; this guards the interface staying sufficient.
        IconStorage api = storage;
        storage.loadPlayer(alice);
        api.setIcon(alice, "base", "CHEST");
        assertNotNull(api.icon(alice, "base"));
        api.flush();
        api.close();
    }
}
