package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.effect.TeleportEffect;
import net.william278.huskhomes.event.TeleportEvent;
import net.william278.huskhomes.event.TeleportWarmupEvent;
import net.william278.huskhomes.position.Home;
import net.william278.huskhomes.teleport.Teleport;
import net.william278.huskhomes.teleport.Teleportable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Plays the departure and arrival effects around a HuskHomes home teleport. The HuskHomes half of
 * {@link TeleportListener}.
 *
 * <p>HuskHomes fires {@code TeleportWarmupEvent} when a timed teleport starts counting down, and
 * {@code TeleportEvent} when a teleport is about to be carried out. Only the second one fires for a
 * teleport with no warmup at all, so both are handled and the entry effect is started by whichever
 * arrives first.
 *
 * <p><b>The warmup cannot be stretched here.</b> EssentialsX exposes {@code setDelay} on its warmup
 * event, so the EssentialsX path lengthens the countdown to fit a declared animation. HuskHomes'
 * {@code TeleportWarmupEvent} has {@code getWarmupDuration()} and no setter, and its duration comes
 * from the player's own {@code huskhomes.teleport_warmup.<n>} permission, so there is nothing to
 * write. The entry effect therefore plays for its declared duration alongside whatever warmup
 * HuskHomes configured; if that warmup is shorter, the player is moved while the animation is still
 * running. That is a real difference between the two backends, not something worked around here — an
 * operator who wants the full animation sets HuskHomes' own warmup to at least
 * {@code teleport-effects.entry.duration}.
 */
public final class HuskHomesTeleportListener implements Listener {

    private final Plugin plugin;
    private final PluginConfig config;
    private final TeleportEffect effect;

    /**
     * Players whose entry effect has already been started for the teleport in flight.
     *
     * <p>Needed because the warmup event and the teleport event both fire for the same home teleport,
     * and playing the departure animation twice reads as a stutter.
     */
    private final Set<UUID> decorating = new HashSet<>();

    public HuskHomesTeleportListener(Plugin plugin, PluginConfig config, TeleportEffect effect) {
        this.plugin = plugin;
        this.config = config;
        this.effect = effect;
    }

    /**
     * Starts the departure effect when the countdown begins. MONITOR, so a cancellation by any other
     * plugin has already happened and an animation is not played for a teleport that will not occur.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarmup(TeleportWarmupEvent event) {
        Player player = homeTeleportPlayer(event.getTimedTeleport());
        if (player != null && decorating.add(player.getUniqueId())) {
            effect.playEntry(player, config.entry());
        }
    }

    /**
     * Starts the departure effect for a teleport with no warmup, and fires the arrival effect.
     *
     * <p>The arrival is scheduled for the next tick because this event fires immediately <em>before</em>
     * the move, exactly as on the EssentialsX path: drawing at the destination now would draw it
     * around a player who is still standing at the origin.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(TeleportEvent event) {
        Player player = homeTeleportPlayer(event.getTeleport());
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (decorating.add(id)) {
            // No warmup event fired for this one, so the entry effect has not played yet. It gets the
            // one tick before the move rather than a full animation, which is all an instant teleport
            // can honestly give it.
            effect.playEntry(player, config.entry());
        }
        decorating.remove(id);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                effect.playArrival(player, player.getLocation(), config.arrival());
            }
        });
    }

    /**
     * The Bukkit player being moved, but only for a teleport whose destination is a home.
     *
     * <p>HuskHomes' {@code Teleport.Type} has no HOME value — a home teleport is an ordinary
     * {@code TELEPORT} whose target happens to be a {@code Home} — so the target's type is what
     * identifies it. Without this check a {@code /tpa}, a {@code /warp} or a {@code /back} would be
     * decorated with the home effect.
     */
    private @Nullable Player homeTeleportPlayer(@Nullable Teleport teleport) {
        if (teleport == null || !(teleport.getTarget() instanceof Home)) {
            return null;
        }
        Teleportable teleporter = teleport.getTeleporter();
        // getTeleporter, not getExecutor: a teleport an admin triggered for somebody else has two
        // different users, and the effect belongs to whoever is actually being moved.
        if (teleporter == null || teleporter.getName() == null) {
            return null;
        }
        return Bukkit.getPlayerExact(teleporter.getName());
    }

    /** Drops a pending mark so a disconnect mid-warmup does not leak one forever. */
    public void forget(UUID playerId) {
        decorating.remove(playerId);
    }

    public void clear() {
        decorating.clear();
    }
}
