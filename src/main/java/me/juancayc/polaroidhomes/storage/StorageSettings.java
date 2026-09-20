package me.juancayc.polaroidhomes.storage;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

/**
 * Immutable snapshot of {@code data.yml}.
 *
 * <p>Storage settings live only here. Nothing about the backend belongs in {@code config.yml}: an
 * operator who edits a JDBC URL expects it to be read on restart, not on the next GUI reload.
 *
 * <p>{@link #describesSamePool(StorageSettings)} exists so a reload can tell whether the operator
 * changed the backend under a live pool. Pool size is deliberately excluded from that comparison:
 * resizing a Hikari pool also needs a restart, but it is not a different database, and warning
 * about it would be noise.
 */
public record StorageSettings(StorageType type,
                              String sqliteFile,
                              String host,
                              int port,
                              String database,
                              String username,
                              String password,
                              int poolSize) {

    public static StorageSettings from(ConfigurationSection section) {
        StorageType type = StorageType.parse(section.getString("type", "sqlite"));
        String file = section.getString("sqlite.file", "homes.db");
        if (file == null || file.isBlank()) {
            file = "homes.db";
        }
        return new StorageSettings(
                type,
                file,
                section.getString("mysql.host", "127.0.0.1"),
                section.getInt("mysql.port", 3306),
                section.getString("mysql.database", "polaroidhomes"),
                section.getString("mysql.username", "root"),
                section.getString("mysql.password", ""),
                // A pool below 1 cannot serve anybody, and Hikari rejects it outright.
                Math.max(1, section.getInt("mysql.pool-size", 8)));
    }

    /** True when both snapshots point at the same physical database with the same driver. */
    public boolean describesSamePool(StorageSettings other) {
        if (other == null || type != other.type) {
            return false;
        }
        return type == StorageType.SQLITE
                ? sqliteFile.equals(other.sqliteFile)
                : Objects.equals(host, other.host)
                        && port == other.port
                        && Objects.equals(database, other.database)
                        && Objects.equals(username, other.username);
    }
}
