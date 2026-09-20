package me.juancayc.polaroidhomes.icon;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YAML-backed icon store: {@code icons.yml}, one section per player uuid.
 *
 * <p>The whole file is held in memory and written only when something changed. Icon changes are
 * rare (a player picks one and forgets about it), so a dirty flag plus an interval flush keeps the
 * main thread clear of disk I/O without any threading of its own.
 */
public final class YamlIconStorage implements IconStorage {

    private final File file;
    private final Logger logger;
    private final YamlConfiguration data;
    private boolean dirty;

    public YamlIconStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public @Nullable String icon(UUID player, String home) {
        return data.getString(path(player, home), null);
    }

    @Override
    public void setIcon(UUID player, String home, @Nullable String reference) {
        data.set(path(player, home), reference);
        dirty = true;
    }

    @Override
    public void renameHome(UUID player, String from, String to) {
        String existing = icon(player, from);
        if (existing == null) {
            return;
        }
        data.set(path(player, from), null);
        data.set(path(player, to), existing);
        dirty = true;
    }

    @Override
    public void deleteHome(UUID player, String home) {
        if (data.get(path(player, home)) == null) {
            return;
        }
        data.set(path(player, home), null);
        // A player who deleted their last home leaves an empty section behind. Pruning it keeps the
        // file from accumulating one dead uuid per player who ever used the server.
        ConfigurationSection section = data.getConfigurationSection(player.toString());
        if (section != null && section.getKeys(false).isEmpty()) {
            data.set(player.toString(), null);
        }
        dirty = true;
    }

    @Override
    public void flush() {
        if (!dirty) {
            return;
        }
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            // Staying dirty means the next flush retries. Clearing the flag here would turn a
            // transient disk error into permanent data loss.
            logger.log(Level.SEVERE, "Could not save icons.yml", ex);
        }
    }

    @Override
    public void close() {
        flush();
    }

    /**
     * EssentialsX lowercases home names internally, so the key must be lowercased too or
     * {@code /sethome Base} and {@code /home base} end up with two different icons.
     */
    private static String path(UUID player, String home) {
        return player + "." + home.toLowerCase(Locale.ROOT);
    }
}
