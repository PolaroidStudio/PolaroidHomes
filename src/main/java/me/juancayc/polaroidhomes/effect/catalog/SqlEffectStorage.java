package me.juancayc.polaroidhomes.effect.catalog;

import me.juancayc.polaroidhomes.storage.DatabaseManager;
import me.juancayc.polaroidhomes.storage.StorageException;
import me.juancayc.polaroidhomes.storage.StorageType;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The effect each player has equipped, stored per player alongside the icons.
 *
 * <p>Write-behind through an in-memory cache, exactly as {@code SqlIconStorage} is and for the same
 * reason: JDBC blocks and this is read on the teleport path, which runs on the main thread. A
 * player's row is loaded once off the main thread, every read afterwards hits the cache, and
 * changes are written in one transaction on the save timer and on disable.
 *
 * <p>One row per player, not one per player and category. That is the "exactly one equipped thing"
 * rule expressed in the schema rather than only in code, so a row set by hand cannot produce a
 * state the plugin has no rules for.
 *
 * <p>Unequipping is a removal from the cache plus a dirty marker rather than a stored null, because
 * "never equipped anything" and "took their effect off" must flush differently: the first writes
 * nothing, the second has to DELETE a row that is still in the database.
 */
public final class SqlEffectStorage {

    private static final String TABLE = "player_effects";

    private final DatabaseManager database;

    /** player -&gt; equipped choice. A player with no entry has nothing equipped. */
    private final Map<UUID, EquippedEffect> cache = new ConcurrentHashMap<>();

    /** Players whose row changed since the last successful flush. Guarded by {@code this}. */
    private final Set<UUID> dirty = new LinkedHashSet<>();

    /**
     * Players whose row has been read, including those who had none.
     *
     * <p>Separate from {@link #cache} because "nothing equipped" is a real, common answer that the
     * cache cannot represent — it holds only players who have something on — and the render path
     * must not treat it as "not loaded yet" and block.
     */
    private final Set<UUID> loadedPlayers = ConcurrentHashMap.newKeySet();

    public SqlEffectStorage(DatabaseManager database) {
        this.database = database;
        createSchema();
    }

    // ------------------------------------------------------------------ schema

    /** Runs once, on enable, for the same reason the icon table's DDL does. */
    private void createSchema() {
        // Bounded VARCHAR on the key column because MySQL rejects an unbounded TEXT primary key;
        // SQLite ignores the length, so one statement satisfies both. The category and id are
        // stored as separate columns rather than as one "animation:purple_charge" string, so a
        // category that gains a third value later does not need every stored row rewritten.
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "category VARCHAR(16) NOT NULL,"
                + "effect_id VARCHAR(64) NOT NULL,"
                + "PRIMARY KEY (player_uuid))";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("Could not create the " + TABLE + " table", ex);
        }
    }

    // ------------------------------------------------------------------ loading

    /**
     * Loads one player's row into the cache. Blocking: callers run it off the main thread.
     *
     * <p>A row already dirty wins over the database, for the same reason it does in the icon store:
     * a choice that has not flushed yet is newer than what is on disk.
     */
    public void loadPlayer(UUID player) {
        EquippedEffect loaded = read(player);
        synchronized (this) {
            if (dirty.contains(player)) {
                return;
            }
            if (loaded == null) {
                // Marked as present-and-empty so isLoaded can tell "nothing equipped" from "not
                // read yet"; the map itself holds only real choices, so the key is simply absent.
                cache.remove(player);
                loadedPlayers.add(player);
                return;
            }
            cache.put(player, loaded);
            loadedPlayers.add(player);
        }
    }

    private @Nullable EquippedEffect read(UUID player) {
        String sql = "SELECT category, effect_id FROM " + TABLE + " WHERE player_uuid = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                // Parsed rather than trusted: a hand-edited row naming a category this build does
                // not have resolves to null, which is exactly "nothing equipped".
                return EquippedEffect.of(rows.getString("category"), rows.getString("effect_id"));
            }
        } catch (SQLException ex) {
            throw new StorageException("Could not load the equipped effect for " + player, ex);
        }
    }

    /** Drops a player's cached row. Called on quit, after their change has been written. */
    public void unloadPlayer(UUID player) {
        synchronized (this) {
            // Unloading a player who still owes the database a write would silently lose the change.
            if (dirty.contains(player)) {
                return;
            }
            cache.remove(player);
            loadedPlayers.remove(player);
        }
    }

    /** True when this player's row is in memory, so the teleport path can read it without blocking. */
    public boolean isLoaded(UUID player) {
        return loadedPlayers.contains(player);
    }

    // ------------------------------------------------------------------ reads and writes

    /** What this player has equipped, or null for nothing. Never blocks. */
    public @Nullable EquippedEffect equipped(UUID player) {
        return cache.get(player);
    }

    /** Sets the equipped effect. A null choice unequips back to a clean teleport. */
    public void equip(UUID player, @Nullable EquippedEffect choice) {
        if (choice == null) {
            cache.remove(player);
        } else {
            cache.put(player, choice);
        }
        loadedPlayers.add(player);
        synchronized (this) {
            dirty.add(player);
        }
    }

    /**
     * Writes every dirty row in one transaction. Blocking: it runs on the async save task and once
     * more on disable.
     *
     * <p>The dirty set is snapshotted before the write and only cleared on success, so a failed
     * flush retries on the next tick of the timer instead of dropping the changes.
     */
    public void flush() {
        List<UUID> batch;
        synchronized (this) {
            if (dirty.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(dirty);
        }

        List<UUID> deletes = new ArrayList<>();
        List<UUID> upserts = new ArrayList<>();
        for (UUID player : batch) {
            if (cache.containsKey(player)) {
                upserts.add(player);
            } else {
                deletes.add(player);
            }
        }

        try (Connection connection = database.connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                writeDeletes(connection, deletes);
                writeUpserts(connection, upserts);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new StorageException("Could not flush equipped effect changes", ex);
        }

        synchronized (this) {
            dirty.removeAll(batch);
        }
    }

    private void writeDeletes(Connection connection, List<UUID> players) throws SQLException {
        if (players.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM " + TABLE + " WHERE player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (UUID player : players) {
                statement.setString(1, player.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void writeUpserts(Connection connection, List<UUID> players) throws SQLException {
        if (players.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(upsertSql())) {
            for (UUID player : players) {
                EquippedEffect choice = cache.get(player);
                if (choice == null) {
                    // Unequipped between the partition above and now; the next flush deletes it.
                    continue;
                }
                statement.setString(1, player.toString());
                statement.setString(2, choice.category().storageKey());
                statement.setString(3, choice.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * The one statement that genuinely differs per dialect, exactly as in the icon store: SQLite
     * spells an upsert {@code ON CONFLICT(cols) DO UPDATE} and reads the rejected row through
     * {@code excluded}, while MySQL spells it {@code ON DUPLICATE KEY UPDATE} and reads it through
     * {@code VALUES()}.
     */
    private String upsertSql() {
        String insert = "INSERT INTO " + TABLE
                + " (player_uuid, category, effect_id) VALUES (?, ?, ?) ";
        return database.type() == StorageType.MYSQL
                ? insert + "ON DUPLICATE KEY UPDATE category = VALUES(category), "
                        + "effect_id = VALUES(effect_id)"
                : insert + "ON CONFLICT(player_uuid) DO UPDATE SET category = excluded.category, "
                        + "effect_id = excluded.effect_id";
    }

    public void close() {
        flush();
    }

    /** How many rows are still waiting to be written. */
    public synchronized int pendingWrites() {
        return dirty.size();
    }
}
