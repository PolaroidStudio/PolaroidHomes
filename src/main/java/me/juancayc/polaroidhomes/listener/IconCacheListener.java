package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.icon.SqlIconStorage;
import me.juancayc.polaroidhomes.storage.StorageException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Keeps a player's icon rows in memory for as long as they are online.
 *
 * <p>The menu renderer reads icons on the main thread while building an inventory, so the rows have
 * to already be in memory by then. Loading them on join — off the main thread, since JDBC blocks —
 * is what lets {@code IconStorage} stay a plain synchronous interface.
 *
 * <p>Neither handler touches the database on the server thread: the join load is dispatched to the
 * async pool and the quit path only writes back through the same async task.
 */
public final class IconCacheListener implements Listener {

    private final Plugin plugin;
    private final SqlIconStorage icons;

    public IconCacheListener(Plugin plugin, SqlIconStorage icons) {
        this.plugin = plugin;
        this.icons = icons;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> load(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Flushing before unloading is what makes the unload safe: unloadPlayer refuses to
                // drop rows that still owe the database a write, so an un-flushed pick would
                // otherwise pin the player in memory until the save timer caught up.
                icons.flush();
            } catch (StorageException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not flush icons on quit; the save timer will retry.", ex);
            }
            icons.unloadPlayer(player);
        });
    }

    /**
     * Loads a player's rows off the main thread. Also used when an admin opens somebody else's
     * menu, where the target may be offline and therefore not cached by the join handler.
     */
    public void loadAsync(UUID player, Runnable whenDone) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            load(player);
            if (whenDone != null) {
                plugin.getServer().getScheduler().runTask(plugin, whenDone);
            }
        });
    }

    private void load(UUID player) {
        try {
            icons.loadPlayer(player);
        } catch (StorageException ex) {
            // A failed load renders default icons rather than refusing the menu. Nothing is
            // overwritten, because a pick only becomes dirty when the player actually makes one.
            plugin.getLogger().log(Level.WARNING, "Could not load icons for " + player, ex);
        }
    }
}
