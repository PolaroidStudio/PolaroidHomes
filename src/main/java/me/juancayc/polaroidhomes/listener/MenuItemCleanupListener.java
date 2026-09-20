package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.menu.MenuItemMarker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Destroys any menu chrome that reached the world.
 *
 * <p>These two paths exist because a marked item that escapes a window becomes an ordinary item
 * entity that any player can pick up. Deleting it on sight, rather than only refusing the drop,
 * keeps a dropped button from becoming a second copy in somebody else's inventory.
 */
public final class MenuItemCleanupListener implements Listener {

    private final MenuItemMarker marker;

    public MenuItemCleanupListener(MenuItemMarker marker) {
        this.marker = marker;
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (marker.isMarked(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (marker.isMarked(event.getItemDrop().getItemStack())) {
            // Removed rather than cancelled: cancelling would hand the button back to the player,
            // which is the state this listener exists to end.
            event.getItemDrop().remove();
        }
    }
}
