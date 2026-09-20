package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.icon.IconStorage;
import net.ess3.api.IUser;
import net.essentialsx.api.v2.events.HomeModifyEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Keeps stored icons in step with the homes they belong to.
 *
 * <p>Icons are this plugin's own storage, keyed by home name, because EssentialsX exposes no
 * metadata API to hang one off. That key is exactly what breaks when a home is renamed or deleted:
 * without this listener {@code /delhome base} leaves a row nothing will ever read, and
 * {@code /renamehome base cabin} leaves the icon on the old name while the renamed home silently
 * falls back to the default picture.
 *
 * <p>EssentialsX's own {@code HomeModifyEvent} carries the cause and both names, so the rename is
 * handled as a rename rather than guessed from a before-and-after diff of the home list.
 */
public final class IconLifecycleListener implements Listener {

    private final IconStorage icons;

    public IconLifecycleListener(IconStorage icons) {
        this.icons = icons;
    }

    /**
     * Runs at MONITOR and ignores cancelled events, so the store is only touched for a change
     * EssentialsX actually went through with.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHomeModify(HomeModifyEvent event) {
        UUID owner = ownerId(event);
        if (owner == null) {
            return;
        }
        switch (event.getCause()) {
            case RENAME -> {
                String from = event.getOldName();
                String to = event.getNewName();
                if (from != null && to != null && !from.equalsIgnoreCase(to)) {
                    icons.renameHome(owner, from, to);
                }
            }
            case DELETE -> {
                // On a delete the name that went away is the old one; EssentialsX leaves the new
                // name null for this cause.
                String removed = event.getOldName() != null ? event.getOldName() : event.getNewName();
                if (removed != null) {
                    icons.deleteHome(owner, removed);
                }
            }
            // CREATE and UPDATE leave the name alone, so the icon key is still correct. A new home
            // simply has no icon yet and renders with the configured default.
            case CREATE, UPDATE -> {
            }
        }
    }

    /**
     * The uuid the icon is keyed under is the home's OWNER, not whoever ran the command. An admin
     * renaming somebody else's home must move that player's icon, not create a row under their own.
     */
    private static UUID ownerId(HomeModifyEvent event) {
        IUser owner = event.getHomeOwner();
        if (owner == null) {
            return null;
        }
        Player base = owner.getBase();
        return base == null ? null : base.getUniqueId();
    }
}
