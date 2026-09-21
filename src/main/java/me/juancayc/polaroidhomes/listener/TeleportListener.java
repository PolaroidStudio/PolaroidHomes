package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.effect.ArrivalWatcher;
import me.juancayc.polaroidhomes.effect.TeleportEffect;
import net.ess3.api.events.UserTeleportHomeEvent;
import net.ess3.api.events.teleport.PreTeleportEvent;
import net.ess3.api.events.teleport.TeleportWarmupEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plays the departure and arrival effects around an EssentialsX home teleport. The EssentialsX half
 * of {@link HuskHomesTeleportListener}; only the listener matching the selected provider is
 * registered.
 *
 * <p>The event chain EssentialsX gives us is: {@code UserTeleportHomeEvent} (before the warmup),
 * then {@code TeleportWarmupEvent} (where the delay can still be changed), then
 * {@code PreTeleportEvent} (after the warmup, immediately before the move). There is no
 * post-teleport event anywhere in that API, so the arrival effect is fired here, on the tick after
 * PreTeleport, rather than waited for.
 *
 * <p>Ordering is what makes the entry effect visible: the warmup is stretched to the declared
 * animation duration in TeleportWarmupEvent and the animation starts there, so it has the whole
 * warmup to play before the player is moved.
 */
public final class TeleportListener implements Listener {

    private final Plugin plugin;
    private final PluginConfig config;
    private final TeleportEffect effect;

    /**
     * Players currently inside a home teleport we are decorating.
     *
     * <p>Needed because PreTeleportEvent fires for every kind of teleport EssentialsX performs, not
     * just homes, and decorating a /tpa or a /warp with the home effect would be wrong.
     */
    private final Map<UUID, Boolean> pending = new HashMap<>();

    public TeleportListener(Plugin plugin, PluginConfig config, TeleportEffect effect) {
        this.plugin = plugin;
        this.config = config;
        this.effect = effect;
    }

    /**
     * Marks the teleport as ours. Runs at MONITOR so a cancellation by any other plugin has already
     * happened and we do not decorate a teleport that will not occur.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHomeTeleport(UserTeleportHomeEvent event) {
        Player player = event.getUser().getBase();
        if (player != null) {
            pending.put(player.getUniqueId(), Boolean.TRUE);
        }
    }

    /**
     * Stretches the warmup to the declared entry duration and starts the departure effect.
     *
     * <p>The warmup is only ever extended, never shortened: a server that configured a longer
     * teleport delay did so deliberately, and cutting it would turn a cosmetic plugin into one that
     * weakens a combat rule.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWarmup(TeleportWarmupEvent event) {
        // getTeleportee, not getTeleporter: a /tphere-style teleport has two different users, and
        // the effect belongs to whoever is actually being moved.
        Player player = event.getTeleportee().getBase();
        if (player == null || !pending.containsKey(player.getUniqueId())) {
            return;
        }

        long wanted = effect.warmupTicks(config.entry());
        if (wanted > 0) {
            // EssentialsX states the delay in seconds; ticks divided back up, rounded so a
            // fractional second is never truncated to a shorter warmup than the animation needs.
            double wantedSeconds = wanted / 20.0D;
            if (wantedSeconds > event.getDelay()) {
                event.setDelay(wantedSeconds);
            }
        }
        effect.playEntry(player, config.entry());
    }

    /**
     * Fires the arrival effect.
     *
     * <p>The arrival is watched for rather than scheduled: this event fires immediately
     * <em>before</em> the move, and the move itself is not guaranteed to have happened by any
     * particular tick.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreTeleport(PreTeleportEvent event) {
        Player player = event.getTeleportee().getBase();
        if (player == null || pending.remove(player.getUniqueId()) == null) {
            return;
        }
        // Watched rather than scheduled for the next tick: a cross-world teleport has often not
        // landed one tick after this event, and the arrival would then be drawn at the origin.
        ArrivalWatcher.await(plugin, player, player.getLocation(),
                (arrived, destination) -> effect.playArrival(arrived, destination, config.arrival()));
    }

    /** Drops a pending mark so a disconnect mid-warmup does not leak an entry forever. */
    public void forget(UUID playerId) {
        pending.remove(playerId);
    }

    public void clear() {
        pending.clear();
    }
}
