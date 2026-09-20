package me.juancayc.polaroidhomes.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Owns the one {@link HikariDataSource} for the plugin's lifetime.
 *
 * <p>Opened once on enable and closed once on disable. A plugin reload must never construct a
 * second one: reopening a pool re-runs driver discovery and the schema check on the main thread,
 * and any connection a flush is holding at that moment belongs to a source nobody will close.
 */
public final class DatabaseManager implements AutoCloseable {

    private final StorageSettings settings;
    private final HikariDataSource dataSource;

    public DatabaseManager(StorageSettings settings, File dataFolder) {
        this.settings = settings;
        this.dataSource = settings.type() == StorageType.SQLITE
                ? openSqlite(settings, dataFolder)
                : openMysql(settings);
    }

    private static HikariDataSource openSqlite(StorageSettings settings, File dataFolder) {
        // The database lives under data/, never beside the YAML configs: a reload that lists the
        // data folder would otherwise try to parse a binary .db as configuration.
        File directory = new File(dataFolder, "data");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new StorageException("Could not create " + directory.getAbsolutePath(), null);
        }
        File file = new File(directory, settings.sqliteFile());

        HikariConfig config = baseConfig("PolaroidHomes-SQLite");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        // SQLite has exactly one writer. Extra connections do not add throughput; they queue on the
        // write lock and starve whoever asks for a connection next.
        config.setMaximumPoolSize(1);
        config.addDataSourceProperty("foreign_keys", "true");
        return new HikariDataSource(config);
    }

    private static HikariDataSource openMysql(StorageSettings settings) {
        HikariConfig config = baseConfig("PolaroidHomes-MySQL");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + settings.host() + ":" + settings.port() + "/"
                + settings.database() + "?useUnicode=true&characterEncoding=utf8");
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        return new HikariDataSource(config);
    }

    private static HikariConfig baseConfig(String poolName) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setConnectionTestQuery("SELECT 1");
        return config;
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public StorageType type() {
        return settings.type();
    }

    /** The settings this pool was opened with, so a reload can detect an operator edit. */
    public StorageSettings settings() {
        return settings;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
