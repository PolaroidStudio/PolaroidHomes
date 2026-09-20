package me.juancayc.polaroidhomes.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Identity and state for one open menu window.
 *
 * <p>Every window this plugin opens is recognised by being an instance of this type, never by its
 * title. Titles are coloured, translated and player-substituted, so matching on one is both
 * spoofable by a player who names a home after the menu title and fragile across translations.
 *
 * <p>Every slot in every window of this plugin is chrome: the player owns nothing inside it. That
 * classification is what makes cancel-everything the correct behaviour here, and it is why no
 * persistence path needs guarding.
 */
public abstract class MenuHolder implements InventoryHolder {

    private final Map<Integer, SlotHandler> handlers = new HashMap<>();
    private Inventory inventory;

    /**
     * Set while the window is being rebuilt.
     *
     * <p>A click that lands during a rebuild would dispatch against a slot map that is half the old
     * layout and half the new one, so the listener drops clicks while this is up.
     */
    private boolean updating;

    protected void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Map<Integer, SlotHandler> handlers() {
        return handlers;
    }

    public @Nullable SlotHandler handler(int rawSlot) {
        return handlers.get(rawSlot);
    }

    public boolean isUpdating() {
        return updating;
    }

    protected void beginUpdate() {
        updating = true;
    }

    protected void endUpdate() {
        updating = false;
    }

    protected void clearHandlers() {
        handlers.clear();
    }

    /** Rebuilds the window's contents in place, for example after a page flip. */
    public abstract void render(Player viewer);
}
