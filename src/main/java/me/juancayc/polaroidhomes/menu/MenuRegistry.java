package me.juancayc.polaroidhomes.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The server-side record of who has one of this plugin's menus open.
 *
 * <p>Keyed by UUID, never by name: a nickname plugin or an offline-mode name reuse turns a
 * name-keyed registry into a window handed to the wrong player.
 *
 * <p>This registry, not {@code getOpenInventory()}, is the authority on what is open. The client
 * decides when the server hears about a close, so anything derived from the live view can be
 * suppressed; a registry the plugin writes itself cannot.
 */
public final class MenuRegistry {

    private final Plugin plugin;
    private final Map<UUID, MenuHolder> open = new HashMap<>();

    public MenuRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public @Nullable MenuHolder get(Player player) {
        return open.get(player.getUniqueId());
    }

    /**
     * Opens a menu for a player, replacing whatever this plugin had open for them.
     *
     * <p>The old entry is dropped before the new one is written, and the replaced window is never
     * closed explicitly: calling closeInventory() here fires a close event that races the open
     * below and deregisters the window that was just installed.
     */
    public void open(Player player, MenuHolder holder) {
        open.remove(player.getUniqueId());
        open.put(player.getUniqueId(), holder);
        holder.render(player);
        player.openInventory(holder.getInventory());
    }

    /** Drops the player's entry. Safe to call for a player who has nothing open. */
    public void close(Player player) {
        open.remove(player.getUniqueId());
    }

    public void closeAll() {
        // Copied first: closeInventory fires a close event that would otherwise mutate the map
        // being iterated.
        for (UUID id : Map.copyOf(open).keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.closeInventory();
            }
        }
        open.clear();
    }

    public Plugin plugin() {
        return plugin;
    }
}
