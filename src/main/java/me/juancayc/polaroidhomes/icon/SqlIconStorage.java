package me.juancayc.polaroidhomes.icon;

import me.juancayc.polaroidhomes.storage.DatabaseManager;
import me.juancayc.polaroidhomes.storage.StorageException;
import me.juancayc.polaroidhomes.storage.StorageType;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL-backed icon store, write-behind through an in-memory cache.
 *
 * <p>JDBC blocks, and {@link IconStorage} is called straight from the inventory renderer on the
 * main thread. So no interface method touches the database: a player's rows are loaded once by
 * {@link #loadPlayer} off the main thread, every read and write afterwards hits the cache, and
 * dirty rows are written in one transaction by {@link #flush()} on the save timer and on disable.
 *
 * <p>Icon changes are a rare, user-initiated event, so this cache exists for latency on the menu
 * render rather than for write volume: a per-pick UPSERT would be affordable, a per-render
 * <em>read</em> on the main thread would not.
 *
 * <p>A cleared icon is a removal from the cache plus a dirty marker rather than a stored null,
 * because "never chosen" and "reset back to the default" must flush differently: the first writes
 * nothing, the second has to DELETE a row that is still in the database.
 */
public final class SqlIconStorage implements IconStorage {

    private static final String TABLE = "home_icons";

    private final DatabaseManager database;

    /** player -&gt; (lowercase home name -&gt; icon reference). */
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();

    /** Rows changed since the last successful flush. Guarded by {@code this}. */
    private final Set<Key> dirty = new LinkedHashSet<>();

    public SqlIconStorage(DatabaseManager database) {
        this.database = database;
        createSchema();
    }

    /** Composite primary key of the table, and the unit of dirtiness. */
    private record Key(UUID player, String home) {
    }

    // ------------------------------------------------------------------ schema

    /**
     * Runs once, on enable. Never called from a reload: re-running DDL costs a round trip and a
     * MySQL metadata lock for a table that has not changed.
     */
    private void createSchema() {
        // The column widths are the only dialect concession here. SQLite ignores VARCHAR lengths,
        // but MySQL needs bounded key columns: a composite PRIMARY KEY over unbounded TEXT is
        // rejected outright, so one statement that satisfies MySQL is valid on both.
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "home_name VARCHAR(64) NOT NULL,"
                + "icon VARCHAR(255) NOT NULL,"
                + "PRIMARY KEY (player_uuid, home_name))";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new StorageException("Could not create the " + TABLE + " table", ex);
        }
    }

    // ------------------------------------------------------------------ loading

    /**
     * Loads one player's rows into the cache. Blocking: callers run it off the main thread, on join
     * or when an admin opens somebody else's menu.
     *
     * <p>Scoped to one uuid on purpose. Selecting the whole table on enable would grow with every
     * player who ever used the server.
     *
     * <p>Rows already dirty win over the database: a pick that has not flushed yet is newer than
     * what is on disk, and a second load must not resurrect the old icon over it.
     */
    public void loadPlayer(UUID player) {
        Map<String, String> loaded = new HashMap<>();
        String sql = "SELECT home_name, icon FROM " + TABLE + " WHERE player_uuid = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    loaded.put(rows.getString("home_name"), rows.getString("icon"));
                }
            }
        } catch (SQLException ex) {
            throw new StorageException("Could not load icons for " + player, ex);
        }

        Map<String, String> target =
                cache.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
        synchronized (this) {
            loaded.forEach((home, icon) -> {
                if (!dirty.contains(new Key(player, home))) {
                    target.put(home, icon);
                }
            });
        }
    }

    /** Drops a player's cached rows. Called on quit, after their changes have been written. */
    public void unloadPlayer(UUID player) {
        synchronized (this) {
            // Unloading a player who still owes the database a write would silently lose that pick.
            if (dirty.stream().anyMatch(key -> key.player().equals(player))) {
                return;
            }
            cache.remove(player);
        }
    }

    /** True when this player's rows are in memory, so the menu can render without blocking. */
    public boolean isLoaded(UUID player) {
        return cache.containsKey(player);
    }

    // ------------------------------------------------------------------ IconStorage

    @Override
    public @Nullable String icon(UUID player, String home) {
        Map<String, String> rows = cache.get(player);
        return rows == null ? null : rows.get(normalize(home));
    }

    @Override
    public void setIcon(UUID player, String home, @Nullable String reference) {
        String key = normalize(home);
        Map<String, String> rows =
                cache.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
        if (reference == null) {
            rows.remove(key);
        } else {
            rows.put(key, reference);
        }
        markDirty(player, key);
    }

    @Override
    public void renameHome(UUID player, String from, String to) {
        String fromKey = normalize(from);
        String toKey = normalize(to);
        if (fromKey.equals(toKey)) {
            return;
        }
        Map<String, String> rows = cache.get(player);
        String existing = rows == null ? null : rows.get(fromKey);
        if (existing == null) {
            return;
        }
        rows.remove(fromKey);
        rows.put(toKey, existing);
        // Both keys are dirty: the source row has to be deleted and the destination inserted. A
        // rename that only marked the destination would leave the old row orphaned in the table.
        markDirty(player, fromKey);
        markDirty(player, toKey);
    }

    @Override
    public void deleteHome(UUID player, String home) {
        String key = normalize(home);
        Map<String, String> rows = cache.get(player);
        if (rows == null || !rows.containsKey(key)) {
            return;
        }
        rows.remove(key);
        markDirty(player, key);
    }

    /**
     * Writes every dirty row in one transaction. Blocking: it runs on the async save task and once
     * more on disable.
     *
     * <p>The dirty set is snapshotted before the write and only cleared on success, so a failed
     * flush retries on the next tick of the timer instead of dropping the changes. Keys marked
     * while the transaction was running stay dirty and belong to the next flush.
     */
    @Override
    public void flush() {
        List<Key> batch;
        synchronized (this) {
            if (dirty.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(dirty);
        }

        List<Key> deletes = new ArrayList<>();
        List<Key> upserts = new ArrayList<>();
        for (Key key : batch) {
            if (icon(key.player(), key.home()) == null) {
                deletes.add(key);
            } else {
                upserts.add(key);
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
            throw new StorageException("Could not flush icon changes", ex);
        }

        synchronized (this) {
            dirty.removeAll(batch);
        }
    }

    private void writeDeletes(Connection connection, List<Key> keys) throws SQLException {
        if (keys.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM " + TABLE + " WHERE player_uuid = ? AND home_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Key key : keys) {
                statement.setString(1, key.player().toString());
                statement.setString(2, key.home());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void writeUpserts(Connection connection, List<Key> keys) throws SQLException {
        if (keys.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(upsertSql())) {
            for (Key key : keys) {
                String icon = icon(key.player(), key.home());
                if (icon == null) {
                    // Cleared between the partition above and now; the next flush deletes it.
                    continue;
                }
                statement.setString(1, key.player().toString());
                statement.setString(2, key.home());
                statement.setString(3, icon);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * The one statement that genuinely differs per dialect: SQLite spells an upsert
     * {@code ON CONFLICT(cols) DO UPDATE} and reads the rejected row through {@code excluded},
     * while MySQL spells it {@code ON DUPLICATE KEY UPDATE}, has no conflict-target clause at all,
     * and reads it through {@code VALUES()}.
     */
    private String upsertSql() {
        String insert = "INSERT INTO " + TABLE + " (player_uuid, home_name, icon) VALUES (?, ?, ?) ";
        return database.type() == StorageType.MYSQL
                ? insert + "ON DUPLICATE KEY UPDATE icon = VALUES(icon)"
                : insert + "ON CONFLICT(player_uuid, home_name) DO UPDATE SET icon = excluded.icon";
    }

    @Override
    public void close() {
        flush();
    }

    /** How many rows are still waiting to be written. */
    public synchronized int pendingWrites() {
        return dirty.size();
    }

    private void markDirty(UUID player, String normalizedHome) {
        synchronized (this) {
            dirty.add(new Key(player, normalizedHome));
        }
    }

    /**
     * EssentialsX lowercases home names internally, so the key must be lowercased too or
     * {@code /sethome Base} and {@code /home base} end up with two different icons.
     */
    private static String normalize(String home) {
        return Objects.requireNonNull(home, "home").toLowerCase(Locale.ROOT);
    }
}
