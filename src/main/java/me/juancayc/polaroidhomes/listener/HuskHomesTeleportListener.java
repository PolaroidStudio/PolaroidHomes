package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.effect.ArrivalWatcher;
import me.juancayc.polaroidhomes.effect.EffectPlayer;
import me.juancayc.polaroidhomes.effect.catalog.EffectEntry;
import me.juancayc.polaroidhomes.teleport.PendingTpaRequests;
import net.william278.huskhomes.event.ReplyTeleportRequestEvent;
import net.william278.huskhomes.event.TeleportEvent;
import net.william278.huskhomes.event.TeleportWarmupEvent;
import net.william278.huskhomes.position.Home;
import net.william278.huskhomes.teleport.Teleport;
import net.william278.huskhomes.teleport.TeleportRequest;
import net.william278.huskhomes.teleport.Teleportable;
import net.william278.huskhomes.teleport.Username;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
 * event, so the EssentialsX path lengthens the countdown to fit the traveller's equipped animation.
 * HuskHomes' {@code TeleportWarmupEvent} has {@code getWarmupDuration()} and no setter, and its
 * duration comes from the player's own {@code huskhomes.teleport_warmup.<n>} permission, so there
 * is nothing to write. The entry effect therefore plays for its declared duration alongside
 * whatever warmup HuskHomes configured; if that warmup is shorter, the player is moved while the
 * animation is still running. That is a real difference between the two backends, not something
 * worked around here.
 *
 * <p>Per-player effects make that gap harder for an operator to close, not easier. There is no
 * longer one duration to set HuskHomes' warmup against: each catalog animation declares its own, so
 * an operator who wants every effect to finish has to set the warmup to the longest one in the
 * catalog, and every shorter effect then leaves the player standing still after it has ended.
 * Keeping the catalog's animation durations close together is the practical answer, and a particle
 * entry has no duration to miss at all.
 *
 * <h2>How an accepted tpa is recognised</h2>
 *
 * <p>HuskHomes states the direction plainly, but only on the request. {@code TeleportRequest.Type}
 * has explicit {@code TPA} and {@code TPA_HERE} values, and {@code ReplyTeleportRequestEvent}
 * carries the request together with {@code isAccepted()}, so the acceptance is a single event
 * naming both the direction and the traveller. What it does not do is reach the teleport: the move
 * that follows is a {@code Teleport} of type {@code TELEPORT} whose target is a
 * {@code Username}, which is also what {@code /tpaccept} on a tpahere, and other username-targeted
 * teleports, produce.
 *
 * <p>So the acceptance is recorded in {@link PendingTpaRequests} against the player it moves, and
 * the teleport side claims that mark. A {@code /tpahere} is never recorded, and a {@code /warp} or
 * {@code /back} has a target that is not a {@code Username} at all, so neither is ever decorated.
 */
public final class HuskHomesTeleportListener implements Listener {

    private final Plugin plugin;
    private final PluginConfig config;
    private final EffectPlayer effects;

    /**
     * Players whose entry effect has already been started for the teleport in flight.
     *
     * <p>Needed because the warmup event and the teleport event both fire for the same home teleport,
     * and playing the departure animation twice reads as a stutter.
     */
    private final Set<UUID> decorating = new HashSet<>();

    /** Accepted {@code /tpa} requests whose teleport has not been claimed yet. */
    private final PendingTpaRequests tpaRequests = new PendingTpaRequests();

    /**
     * Teleports in flight that were claimed as an accepted {@code /tpa}.
     *
     * <p>Held for the same reason {@link #decorating} is: the warmup event and the teleport event
     * both fire for one teleport, and the second must know the first already claimed the mark
     * rather than try to claim an already-consumed one. The value is only a marker now — both
     * kinds of teleport draw the same thing, so there are no per-kind settings left to carry.
     */
    private final Map<UUID, Boolean> decoratingTpa = new HashMap<>();

    public HuskHomesTeleportListener(Plugin plugin, PluginConfig config, EffectPlayer effects) {
        this.plugin = plugin;
        this.config = config;
        this.effects = effects;
    }

    /**
     * Records an accepted {@code /tpa} against the player it is about to move.
     *
     * <p>This event also fires for a decline, and for a tpahere in both directions, which is why
     * both {@code isAccepted()} and the request type are checked. The requester is the traveller
     * for a {@code TPA}; for a {@code TPA_HERE} it would be the recipient, and that case is
     * deliberately dropped rather than recorded under the other name.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReply(ReplyTeleportRequestEvent event) {
        if (!config.tpaEffectsEnabled() || !event.isAccepted()) {
            return;
        }
        TeleportRequest request = event.getRequest();
        if (request == null || request.getRequesterName() == null) {
            return;
        }
        Player traveller = Bukkit.getPlayerExact(request.getRequesterName());
        if (traveller == null) {
            // Cross-server request: the requester is on another backend and there is no Bukkit
            // player here to draw anything around. Nothing to record.
            return;
        }
        tpaRequests.remember(traveller.getUniqueId(),
                request.getType() == TeleportRequest.Type.TPA_HERE, System.currentTimeMillis());
    }

    /**
     * Starts the departure effect when the countdown begins. MONITOR, so a cancellation by any other
     * plugin has already happened and an animation is not played for a teleport that will not occur.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarmup(TeleportWarmupEvent event) {
        Teleport teleport = event.getTimedTeleport();
        Player player = homeTeleportPlayer(teleport);
        if (player != null) {
            if (config.homeEffectsEnabled() && decorating.add(player.getUniqueId())) {
                effects.playEntry(player, effects.resolve(player));
            }
            return;
        }
        player = tpaTeleportPlayer(teleport);
        if (player == null) {
            return;
        }
        // The mark is recorded before the play guard, not inside it: it has already been consumed
        // by tpaTeleportPlayer, so dropping it here would leave the teleport event unable to tell
        // this tpa from an undecorated teleport.
        decoratingTpa.put(player.getUniqueId(), Boolean.TRUE);
        if (decorating.add(player.getUniqueId())) {
            effects.playEntry(player, effects.resolve(player));
        }
    }

    /**
     * Starts the departure effect for a teleport with no warmup, and fires the arrival effect.
     *
     * <p>The arrival is watched for rather than scheduled: this event fires immediately
     * <em>before</em> the move, and the move itself is not guaranteed to have happened by any
     * particular tick.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(TeleportEvent event) {
        Teleport teleport = event.getTeleport();
        Player player = homeTeleportPlayer(teleport);
        if (player != null) {
            if (!config.homeEffectsEnabled()) {
                return;
            }
        } else {
            player = tpaTeleportPlayer(teleport);
            if (player == null) {
                return;
            }
            // The warmup may already have claimed the mark for this same teleport; the remove is
            // what stops the entry below from being read as a second, unclaimed tpa.
            decoratingTpa.remove(player.getUniqueId());
        }
        UUID id = player.getUniqueId();
        // Resolved from what the player is wearing at this moment, once, and reused for both
        // halves so a departure and an arrival can never come from two different effects.
        EffectEntry entry = effects.resolve(player);
        if (decorating.add(id)) {
            // No warmup event fired for this one, so the entry effect has not played yet. It gets the
            // one tick before the move rather than a full animation, which is all an instant teleport
            // can honestly give it.
            effects.playEntry(player, entry);
        }
        decorating.remove(id);

        if (entry == null) {
            // Nothing equipped, or equipped but inert. Watching for the arrival would schedule a
            // per-tick task for an effect that will draw nothing.
            return;
        }
        // Watched rather than scheduled for the next tick: HuskHomes carries the move out
        // asynchronously and a cross-world teleport routinely has not landed a tick later, which
        // drew the arrival around a player still standing at the origin — invisibly, on top of the
        // departure effect just drawn there.
        Player travelling = player;
        ArrivalWatcher.await(plugin, travelling, travelling.getLocation(),
                (arrived, destination) -> effects.playArrival(arrived, destination, entry));
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

    /**
     * The Bukkit player being moved, but only for a teleport that an accepted {@code /tpa} caused.
     *
     * <p>Two things must both hold. The target must be a {@code Username}, which rules out a
     * {@code /warp}, a {@code /back} and a {@code /home} outright, since none of those targets a
     * player. And the traveller must be holding a mark recorded by {@link #onReply}, which is what
     * rules out the remaining username-targeted teleports: a {@code /tpahere}, and a
     * {@code /tp other} an admin ran. The mark is consumed here, so one accepted request decorates
     * exactly one teleport.
     */
    private @Nullable Player tpaTeleportPlayer(@Nullable Teleport teleport) {
        if (!config.tpaEffectsEnabled() || teleport == null
                || !(teleport.getTarget() instanceof Username)) {
            return null;
        }
        Teleportable teleporter = teleport.getTeleporter();
        if (teleporter == null || teleporter.getName() == null) {
            return null;
        }
        Player player = Bukkit.getPlayerExact(teleporter.getName());
        if (player == null) {
            return null;
        }
        UUID id = player.getUniqueId();
        // Already resolved by the warmup event for this same teleport: the mark is spent, and the
        // settings it produced are waiting in decoratingTpa.
        if (decoratingTpa.containsKey(id)) {
            return player;
        }
        return tpaRequests.claim(id, System.currentTimeMillis()) ? player : null;
    }

    /** Drops a pending mark so a disconnect mid-warmup does not leak one forever. */
    public void forget(UUID playerId) {
        decorating.remove(playerId);
        decoratingTpa.remove(playerId);
        tpaRequests.forget(playerId);
    }

    public void clear() {
        decorating.clear();
        decoratingTpa.clear();
        tpaRequests.clear();
    }
}
