package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.text.MessageService;
import net.ess3.api.IUser;
import net.essentialsx.api.v2.events.HomeModifyEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Blocks home creation in a blacklisted world, on EssentialsX. The EssentialsX half of
 * {@link HuskHomesWorldBlacklistListener}; only the listener matching the selected provider is
 * registered, because a class referencing an absent plugin's events cannot be loaded.
 *
 * <p>The backend's own event is hooked rather than its command, which is what makes this work
 * regardless of how the home was made: {@code /sethome}, another plugin calling
 * {@code IUser#setHome}, or an API caller all produce this same {@code HomeModifyEvent}. Gating the
 * command would leave every other route open.
 *
 * <p>Runs at HIGH rather than MONITOR: MONITOR is for observing a decision that has been made, and
 * this is the decision. {@code ignoreCancelled} keeps it from re-reporting a creation somebody else
 * has already refused for their own reason.
 *
 * <p>CREATE and UPDATE are both refused. UPDATE is EssentialsX moving an existing home to a new
 * location, and a home relocated INTO a blocked world is the same problem as one created there.
 * RENAME and DELETE are untouched: a rename does not move the home, and refusing a delete would
 * trap the player with exactly the home the blacklist is telling them to get rid of.
 */
public final class WorldBlacklistListener implements Listener {

    private final Supplier<PluginConfig> config;
    private final Supplier<MessageService> messages;

    /**
     * Both services are read through suppliers rather than held directly, because a reload replaces
     * the message service outright and rebuilds the config's parsed blacklist. A listener holding
     * the old ones would keep enforcing a list the operator has just edited.
     */
    public WorldBlacklistListener(Supplier<PluginConfig> config, Supplier<MessageService> messages) {
        this.config = config;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHomeModify(HomeModifyEvent event) {
        switch (event.getCause()) {
            case CREATE, UPDATE -> {
            }
            // A rename moves no location, and a delete is what the blacklist message asks for.
            case RENAME, DELETE -> {
                return;
            }
        }

        Location location = event.getNewLocation();
        String world = location == null || location.getWorld() == null
                ? null
                : location.getWorld().getName();
        if (!config.get().worldBlacklist().isBlocked(world)) {
            return;
        }

        event.setCancelled(true);
        // Told to whoever ran the command, not to the home's owner: an admin setting somebody
        // else's home is the one who needs to know why it was refused.
        Player actor = base(event.getUser());
        if (actor != null) {
            messages.get().send(actor, "worlds.blocked_create",
                    "%world%", MessageService.escape(world));
        }
    }

    private static @Nullable Player base(@Nullable IUser user) {
        return user == null ? null : user.getBase();
    }
}
