package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.icon.IconStorage;
import net.william278.huskhomes.event.HomeDeleteEvent;
import net.william278.huskhomes.event.HomeEditEvent;
import net.william278.huskhomes.position.Home;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Keeps stored icons in step with HuskHomes' homes. The HuskHomes half of
 * {@link IconLifecycleListener}.
 *
 * <p>Icons are keyed by owner uuid plus home name, because neither backend offers metadata to hang
 * one off. That key is what breaks on a rename or a delete: without this listener a deleted home
 * leaves a row nothing will ever read, and a renamed home silently falls back to the default picture
 * while its icon stays on the old name.
 *
 * <p>HuskHomes has no single "home modified" event and no rename cause. A rename arrives as
 * {@code HomeEditEvent}, which carries the home before and after the edit as two objects, so the
 * rename is detected by comparing their names rather than being announced. An edit that changed the
 * description, the privacy flag or the position leaves both names equal and is correctly ignored.
 *
 * <p>{@code DeleteAllHomesEvent} is deliberately not handled: it names the owner but not the homes,
 * and this plugin's store cannot enumerate a player's rows to drop them. The orphaned rows are
 * harmless — they are keyed to names that no longer resolve, and a home recreated under an old name
 * simply gets its old icon back, which is closer to what the player expects than a default one.
 */
public final class HuskHomesIconLifecycleListener implements Listener {

    private final IconStorage icons;

    public HuskHomesIconLifecycleListener(IconStorage icons) {
        this.icons = icons;
    }

    /**
     * Runs at MONITOR and ignores cancelled events, so the store is only touched for a rename
     * HuskHomes actually went through with.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHomeEdit(HomeEditEvent event) {
        Home before = event.getOriginalHome();
        Home after = event.getHome();
        if (before == null || after == null) {
            return;
        }
        String from = before.getMeta().getName();
        String to = after.getMeta().getName();
        UUID owner = ownerId(after);
        if (owner == null || from == null || to == null || from.equalsIgnoreCase(to)) {
            return;
        }
        icons.renameHome(owner, from, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHomeDelete(HomeDeleteEvent event) {
        Home home = event.getHome();
        if (home == null) {
            return;
        }
        UUID owner = ownerId(home);
        String name = home.getMeta().getName();
        if (owner != null && name != null) {
            icons.deleteHome(owner, name);
        }
    }

    /**
     * The uuid the icon is keyed under is the home's OWNER, not whoever ran the command. An admin
     * renaming somebody else's home must move that player's icon, not create a row under their own —
     * which is why this reads the home's owner and never the event's editor or deleter.
     */
    private static UUID ownerId(Home home) {
        return home.getOwner() == null ? null : home.getOwner().getUuid();
    }
}
