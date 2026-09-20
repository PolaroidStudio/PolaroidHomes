package me.juancayc.polaroidhomes.menu;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Tags every item this plugin renders into a menu.
 *
 * <p>Cancelling clicks is not enough on its own. A chrome item can still reach a player's inventory
 * through a path the click handler never sees, and once it is there it is indistinguishable from a
 * real item. Marking at the single render choke point means an escaped button can always be told
 * apart from a legitimate one and deleted on sight.
 */
public final class MenuItemMarker {

    private final NamespacedKey key;

    public MenuItemMarker(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "menu_item");
    }

    public @Nullable ItemStack mark(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMarked(@Nullable ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Deletes every marked item the player is carrying, then resyncs their client. */
    public void cleanInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            if (isMarked(contents[slot])) {
                player.getInventory().setItem(slot, null);
                changed = true;
            }
        }
        if (isMarked(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            changed = true;
        }
        if (changed) {
            // Without this the client keeps drawing the item it thinks it has, which looks exactly
            // like the dupe the cleanup just prevented.
            player.updateInventory();
        }
    }
}
