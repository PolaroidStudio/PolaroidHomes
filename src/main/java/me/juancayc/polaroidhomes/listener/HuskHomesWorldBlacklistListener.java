package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.text.MessageService;
import net.william278.huskhomes.event.HomeCreateEvent;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.user.CommandUser;
import net.william278.huskhomes.user.OnlineUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Blocks home creation in a blacklisted world, on HuskHomes. The HuskHomes half of
 * {@link WorldBlacklistListener}.
 *
 * <p>{@code HomeCreateEvent} is cancellable and carries the target {@code Position}, whose
 * {@code World} names the world by string rather than by Bukkit object. That is what makes the
 * check work on a proxied network: a home being created on another server names a world this
 * server may not have loaded, and a blacklist entry for it still matches because the comparison
 * never needs a Bukkit world at all.
 *
 * <p>HuskHomes has no equivalent of EssentialsX's UPDATE cause on this event - relocating an
 * existing home goes through {@code HomeEditEvent}, which does not distinguish a move from a
 * description change - so only creation is refused here. A home relocated into a blocked world
 * afterwards is still caught by the menu, which refuses to teleport to it and tells the player to
 * delete it. That gap is stated rather than papered over.
 */
public final class HuskHomesWorldBlacklistListener implements Listener {

    private final Supplier<PluginConfig> config;
    private final Supplier<MessageService> messages;

    /**
     * Both services are read through suppliers, for the same reason the EssentialsX half does it: a
     * reload replaces the message service and rebuilds the parsed blacklist, and a listener holding
     * the old ones would keep enforcing a list the operator has just edited.
     */
    public HuskHomesWorldBlacklistListener(Supplier<PluginConfig> config,
                                           Supplier<MessageService> messages) {
        this.config = config;
        this.messages = messages;
    }

    /**
     * Runs at HIGH rather than MONITOR, because this is the decision rather than an observation of
     * one, and ignores a creation somebody else has already refused.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHomeCreate(HomeCreateEvent event) {
        Position position = event.getPosition();
        String world = position == null || position.getWorld() == null
                ? null
                : position.getWorld().getName();
        if (!config.get().worldBlacklist().isBlocked(world)) {
            return;
        }

        event.setCancelled(true);
        // Told to whoever ran the command, not to the home's owner: an admin setting somebody
        // else's home is the one who needs to know why it was refused.
        Player actor = base(event.getCreator());
        if (actor != null) {
            messages.get().send(actor, "worlds.blocked_create",
                    "%world%", MessageService.escape(world));
        }
    }

    /**
     * The Bukkit player behind a HuskHomes command user.
     *
     * <p>Resolved by uuid through Bukkit rather than through HuskHomes' own adapter, because
     * {@code CommandUser} is also implemented by the console, which carries no uuid at all - only
     * an {@code OnlineUser} is a player. Returning null for the console is correct: HuskHomes
     * already logs the refusal it reads back from the cancelled event.
     */
    private static @Nullable Player base(@Nullable CommandUser user) {
        return user instanceof OnlineUser online ? Bukkit.getPlayer(online.getUuid()) : null;
    }
}
