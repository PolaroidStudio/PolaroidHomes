package me.juancayc.polaroidhomes.storage;

import java.util.Locale;

/** The two supported backends. SQLite is the default because it needs no server to be set up. */
public enum StorageType {
    SQLITE,
    MYSQL;

    /** Unknown values fall back to sqlite rather than failing enable: a typo must not lose homes. */
    public static StorageType parse(String raw) {
        if (raw == null) {
            return SQLITE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "mysql", "mariadb" -> MYSQL;
            default -> SQLITE;
        };
    }
}
