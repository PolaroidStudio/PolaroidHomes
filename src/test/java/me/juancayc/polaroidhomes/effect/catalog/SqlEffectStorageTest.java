package me.juancayc.polaroidhomes.effect.catalog;

import me.juancayc.polaroidhomes.storage.DatabaseManager;
import me.juancayc.polaroidhomes.storage.StorageSettings;
import me.juancayc.polaroidhomes.storage.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the equipped-effect store against a real temp-file SQLite database, exactly as the icon
 * store's test does and for the same reason: the behaviour worth testing is the boundary between
 * the cache and the table, and a stubbed connection would assert on this test's idea of SQL rather
 * than on what SQLite accepts.
 *
 * <p>The one-row-per-player schema is the "exactly one equipped thing" rule written down in a place
 * a hand-edited database cannot violate, so it is pinned here.
 */
class SqlEffectStorageTest {

    @TempDir
    Path folder;

    private DatabaseManager database;
    private SqlEffectStorage storage;

    private final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private final UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

    private static final EquippedEffect PORTAL =
            new EquippedEffect(EffectCategory.PARTICLE, "portal");
    private static final EquippedEffect PURPLE =
            new EquippedEffect(EffectCategory.ANIMATION, "purple_charge");

    @BeforeEach
    void open() {
        this.database = openDatabase();
        this.storage = new SqlEffectStorage(database);
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
    private SqlEffectStorage reopen() {
        database.close();
        this.database = openDatabase();
        return new SqlEffectStorage(database);
    }

    @Test
    void anEquippedEffectSurvivesAFlushAndAReopen() {
        storage.equip(alice, PORTAL);
        storage.flush();

        SqlEffectStorage reopened = reopen();
        reopened.loadPlayer(alice);

        assertEquals(PORTAL, reopened.equipped(alice));
    }

    @Test
    void equippingASecondEffectReplacesTheFirstRatherThanAddingOne() {
        storage.equip(alice, PURPLE);
        storage.flush();
        storage.equip(alice, PORTAL);
        storage.flush();

        SqlEffectStorage reopened = reopen();
        reopened.loadPlayer(alice);

        // One row, and it is the second choice. The schema's single-column primary key is what
        // guarantees the first cannot still be sitting there.
        assertEquals(PORTAL, reopened.equipped(alice));
    }

    @Test
    void unequippingDeletesTheRowRatherThanLeavingAStaleOne() {
        storage.equip(alice, PORTAL);
        storage.flush();

        storage.equip(alice, null);
        storage.flush();

        SqlEffectStorage reopened = reopen();
        reopened.loadPlayer(alice);
        assertNull(reopened.equipped(alice));
    }

    @Test
    void aPlayerWhoNeverEquippedAnythingWritesNothing() {
        storage.loadPlayer(alice);

        assertNull(storage.equipped(alice));
        assertEquals(0, storage.pendingWrites());
    }

    @Test
    void playersDoNotShareRows() {
        storage.equip(alice, PORTAL);
        storage.equip(bob, PURPLE);
        storage.flush();

        SqlEffectStorage reopened = reopen();
        reopened.loadPlayer(alice);
        reopened.loadPlayer(bob);

        assertEquals(PORTAL, reopened.equipped(alice));
        assertEquals(PURPLE, reopened.equipped(bob));
    }

    /**
     * "Nothing equipped" is a real answer, so it has to be distinguishable from "not read yet" or
     * the render path would treat a clean player as one still loading.
     */
    @Test
    void aPlayerWithNoRowStillCountsAsLoaded() {
        assertFalse(storage.isLoaded(alice));

        storage.loadPlayer(alice);

        assertTrue(storage.isLoaded(alice));
        assertNull(storage.equipped(alice));
    }

    @Test
    void anUnflushedChoiceIsNotDroppedByAnUnload() {
        storage.equip(alice, PORTAL);

        storage.unloadPlayer(alice);

        // Still in memory, because dropping it would lose a write the database has never seen.
        assertEquals(PORTAL, storage.equipped(alice));
        assertEquals(1, storage.pendingWrites());
    }

    @Test
    void anUnflushedChoiceWinsOverWhatIsOnDisk() {
        storage.equip(alice, PORTAL);
        storage.flush();
        // Changed but not yet written: a second load must not resurrect the older value over it.
        storage.equip(alice, PURPLE);

        storage.loadPlayer(alice);

        assertEquals(PURPLE, storage.equipped(alice));
    }

    @Test
    void aFlushWithNothingDirtyIsANoOp() {
        storage.flush();

        assertEquals(0, storage.pendingWrites());
    }

    @Test
    void aSuccessfulFlushClearsTheDirtySet() {
        storage.equip(alice, PORTAL);
        assertEquals(1, storage.pendingWrites());

        storage.flush();

        assertEquals(0, storage.pendingWrites());
    }

    /**
     * A row naming a category this build does not have reads as nothing equipped rather than
     * throwing, because the alternative is a player who cannot open their own menu.
     */
    @Test
    void aHandEditedRowNamingAnUnknownCategoryReadsAsNothing() {
        storage.equip(alice, PORTAL);
        storage.flush();
        corruptCategory(alice, "sound");

        SqlEffectStorage reopened = reopen();
        reopened.loadPlayer(alice);

        assertNull(reopened.equipped(alice));
        assertTrue(reopened.isLoaded(alice));
    }

    private void corruptCategory(UUID player, String category) {
        try (var connection = database.connection();
             var statement = connection.prepareStatement(
                     "UPDATE player_effects SET category = ? WHERE player_uuid = ?")) {
            statement.setString(1, category);
            statement.setString(2, player.toString());
            statement.executeUpdate();
        } catch (Exception ex) {
            throw new AssertionError("Could not rewrite the test row", ex);
        }
    }

    @Test
    void creatingTheSchemaTwiceIsSafe() {
        assertNotNull(new SqlEffectStorage(database));
    }
}
