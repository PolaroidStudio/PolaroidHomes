package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.config.EffectSettings;
import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.config.TeleportEffects;
import me.juancayc.polaroidhomes.effect.ArrivalWatcher;
import me.juancayc.polaroidhomes.effect.TeleportEffect;
import me.juancayc.polaroidhomes.teleport.PendingTpaRequests;
import net.ess3.api.events.TPARequestEvent;
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
import org.jetbrains.annotations.Nullable;

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
 *
 * <h2>How an accepted tpa is recognised</h2>
 *
 * <p>It is not announced. {@code TPARequestEvent} fires when the request is <em>made</em>, and it
 * is the only place EssentialsX distinguishes the two directions: {@code isTeleportHere()} is
 * exactly the {@code /tpa} versus {@code /tpahere} flag. The acceptance minutes later produces an
 * ordinary {@code TeleportWarmupEvent} / {@code PreTeleportEvent} pair carrying nothing that says
 * where it came from, because {@code getTeleportCause()} is {@code COMMAND} for a {@code /warp}, a
 * {@code /spawn}, a {@code /back} and a tpa alike.
 *
 * <p>So the request is remembered by {@link PendingTpaRequests}, keyed on the player it would move,
 * and the teleport side claims that mark. A {@code /tpahere} is never recorded, so it is never
 * claimed, and every other command reaches the teleport events with no mark to find and stays
 * undecorated. Home teleports are unaffected: {@code UserTeleportHomeEvent} still marks them, and
 * that mark is checked first.
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

    /** Requests made but not yet accepted, keyed on the player a tpa would move. */
    private final PendingTpaRequests tpaRequests = new PendingTpaRequests();

    /**
     * Teleports in flight that were claimed as an accepted {@code /tpa}, and so are decorated with
     * the tpa settings rather than the home ones.
     *
     * <p>Separate from {@link #pending} rather than folded into it because the two are established
     * at different moments, a home before the warmup and a tpa at the warmup, and a single map
     * would have to encode which kind it held anyway.
     */
    private final Map<UUID, TeleportEffects> decoratingTpa = new HashMap<>();

    public TeleportListener(Plugin plugin, PluginConfig config, TeleportEffect effect) {
        this.plugin = plugin;
        this.config = config;
        this.effect = effect;
    }

    /**
     * Remembers a {@code /tpa} so the teleport it may later cause can be told apart from a
     * {@code /warp}.
     *
     * <p>MONITOR and ignoreCancelled, like the home mark: a request another plugin cancelled will
     * never be accepted, and remembering it would leave a mark only expiry clears.
     *
     * <p>{@code isTeleportHere()} decides, and it decides here rather than at teleport time because
     * this is the only point in the EssentialsX API where the two directions differ at all.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTpaRequest(TPARequestEvent event) {
        if (!config.tpaEffectsEnabled()) {
            return;
        }
        // getRequester is a CommandSource, since the console can send a request on a player's
        // behalf, so the player behind it may be absent and there would be nobody to decorate.
        Player requester = event.getRequester().getPlayer();
        if (requester != null) {
            tpaRequests.remember(requester.getUniqueId(), event.isTeleportHere(),
                    System.currentTimeMillis());
        }
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
        if (player == null) {
            return;
        }
        EffectSettings entry = entryFor(player);
        if (entry == null) {
            return;
        }

        long wanted = effect.warmupTicks(entry);
        if (wanted > 0) {
            // EssentialsX states the delay in seconds; ticks divided back up, rounded so a
            // fractional second is never truncated to a shorter warmup than the animation needs.
            double wantedSeconds = wanted / 20.0D;
            if (wantedSeconds > event.getDelay()) {
                event.setDelay(wantedSeconds);
            }
        }
        effect.playEntry(player, entry);
    }

    /**
     * The entry settings for a teleport about to warm up, or null when it is not one of ours.
     *
     * <p>The home mark is checked first and consumes nothing else: a home teleport is decorated as
     * a home even for a player who also holds an unaccepted tpa request, because the teleport in
     * front of us is provably the home one.
     */
    private @Nullable EffectSettings entryFor(Player player) {
        UUID id = player.getUniqueId();
        if (pending.containsKey(id)) {
            return config.entry();
        }
        if (config.tpaEffectsEnabled() && tpaRequests.claim(id, System.currentTimeMillis())) {
            TeleportEffects tpa = config.tpaEffects();
            decoratingTpa.put(id, tpa);
            return tpa.entry();
        }
        return null;
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
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        TeleportEffects tpa = decoratingTpa.remove(id);
        EffectSettings arrival;
        if (pending.remove(id) != null) {
            arrival = config.arrival();
        } else if (tpa != null) {
            arrival = tpa.arrival();
        } else {
            // A teleport with no warmup never reached entryFor, so an accepted tpa can still be
            // claimable here. Claiming it now is what decorates a tpa on a server that configured
            // no teleport delay at all.
            if (!config.tpaEffectsEnabled() || !tpaRequests.claim(id, System.currentTimeMillis())) {
                return;
            }
            TeleportEffects settings = config.tpaEffects();
            // The entry gets the one tick before the move rather than a full animation, which is
            // all an instant teleport can honestly give it.
            effect.playEntry(player, settings.entry());
            arrival = settings.arrival();
        }
        // Watched rather than scheduled for the next tick: a cross-world teleport has often not
        // landed one tick after this event, and the arrival would then be drawn at the origin.
        ArrivalWatcher.await(plugin, player, player.getLocation(),
                (arrived, destination) -> effect.playArrival(arrived, destination, arrival));
    }

    /** Drops a pending mark so a disconnect mid-warmup does not leak an entry forever. */
    public void forget(UUID playerId) {
        pending.remove(playerId);
        decoratingTpa.remove(playerId);
        tpaRequests.forget(playerId);
    }

    public void clear() {
        pending.clear();
        decoratingTpa.clear();
        tpaRequests.clear();
    }
}
