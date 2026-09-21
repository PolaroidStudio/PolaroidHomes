package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.edit.DeleteConfirmations;
import me.juancayc.polaroidhomes.menu.MenuHolder;
import me.juancayc.polaroidhomes.menu.MenuItemMarker;
import me.juancayc.polaroidhomes.menu.MenuRegistry;
import me.juancayc.polaroidhomes.menu.SlotHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Click, drag, close and eviction handling for every menu this plugin opens.
 *
 * <p>Every slot in every one of those windows is chrome, so the rule is: cancel first, resolve the
 * action second, and never trust the clicked inventory to tell you which window you are in.
 */
public final class MenuListener implements Listener {

    /**
     * Ordinary dispatch window. Short enough not to be felt, long enough to swallow the duplicate
     * packets a client emits for one physical click.
     */
    private static final long CLICK_COOLDOWN_MILLIS = 75L;

    /**
     * Shift-click gets a longer window because the client reliably sends the same shift-click more
     * than once, and a doubled dispatch on a page flip lands on a slot map that has already moved.
     */
    private static final long SHIFT_COOLDOWN_MILLIS = 200L;

    /** Marked chrome can land in a player's inventory by a path the click handler never sees. */
    private static final long CLEANUP_DELAY_TICKS = 3L;

    private final Plugin plugin;
    private final MenuRegistry registry;
    private final MenuItemMarker marker;

    /**
     * Held only so an armed delete dies with the window that armed it.
     *
     * <p>Without this a player could arm a delete, close the menu, and have it still armed when
     * they reopen within the window - the arming would then be confirmed by the first drop click
     * of a session in which they never saw the warning.
     */
    private final DeleteConfirmations deletes;
    private final Map<UUID, Long> lastDispatch = new HashMap<>();

    public MenuListener(Plugin plugin, MenuRegistry registry, MenuItemMarker marker,
                        DeleteConfirmations deletes) {
        this.plugin = plugin;
        this.registry = registry;
        this.marker = marker;
        this.deletes = deletes;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        // Keyed on the TOP inventory, not on the clicked one. Keying on the clicked inventory
        // leaves clicks in the player's own inventory unhandled, and that is the path shift-click,
        // hotbar number keys and offhand swap use to push a real item into a menu slot.
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }

        // Cancel first and unconditionally. Everything below may return early; none of those exits
        // may leave the event live.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (holder.isUpdating()) {
            // The slot map is half-rebuilt. Dispatching now would run the old slot's handler
            // against the new layout.
            return;
        }

        ClickType click = event.getClick();
        // DOUBLE_CLICK is a collect-to-cursor that vacuums every matching stack in both
        // inventories, so it is dropped outright rather than dispatched.
        if (click == ClickType.DOUBLE_CLICK) {
            return;
        }
        // UNKNOWN and CREATIVE are not part of the normal click chain. Handled explicitly here so
        // they are visibly refused rather than falling through a LEFT/RIGHT/SHIFT test.
        if (click == ClickType.UNKNOWN || click == ClickType.CREATIVE) {
            return;
        }

        // getRawSlot, never getSlot: getSlot aliases top and bottom inventory indices, so a click
        // on the player's own row 1 would resolve to the menu's slot 0.
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }

        if (isOnCooldown(player, click)) {
            return;
        }

        SlotHandler handler = holder.handler(rawSlot);
        if (handler == null) {
            return;
        }
        markDispatched(player);
        // Opening or closing an inventory inside InventoryClickEvent desyncs the client into a
        // ghost cursor item. Every handler is therefore deferred a tick.
        Bukkit.getScheduler().runTask(plugin, () -> handler.onClick(player, click));
    }

    /**
     * Drags are cancelled whenever the top inventory is one of ours.
     *
     * <p>A missing drag handler is the single most commonly omitted listener in this ecosystem and
     * a classic extraction vector: a drag that starts in the player's inventory and ends across
     * menu slots is not an InventoryClickEvent at all.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        registry.close(player);
        lastDispatch.remove(player.getUniqueId());
        deletes.clear(player.getUniqueId());
        // Delayed: a chrome item that escaped lands in the inventory after the close resolves, so
        // sweeping on the same tick would miss it.
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> marker.cleanInventory(player), CLEANUP_DELAY_TICKS);
    }

    /**
     * A foreign open evicts our entry.
     *
     * <p>Another plugin opening a window does not always produce a close event for ours, and a
     * registry entry that outlives its window answers for a window nobody is looking at.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) {
            registry.close(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        registry.close(player);
        lastDispatch.remove(player.getUniqueId());
        deletes.clear(player.getUniqueId());
    }

    /**
     * Join sweep.
     *
     * <p>Independent of the close path on purpose: a player who disconnected while holding a chrome
     * item never produced a usable close, and this is the only place that item gets removed.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> marker.cleanInventory(event.getPlayer()), 10L);
    }

    private boolean isOnCooldown(Player player, ClickType click) {
        Long last = lastDispatch.get(player.getUniqueId());
        if (last == null) {
            return false;
        }
        long window = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT
                ? SHIFT_COOLDOWN_MILLIS
                : CLICK_COOLDOWN_MILLIS;
        return System.currentTimeMillis() - last < window;
    }

    private void markDispatched(Player player) {
        lastDispatch.put(player.getUniqueId(), System.currentTimeMillis());
    }
}
