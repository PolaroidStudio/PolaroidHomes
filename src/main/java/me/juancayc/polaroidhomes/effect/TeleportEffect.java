package me.juancayc.polaroidhomes.effect;

import me.juancayc.polaroidhomes.config.EffectSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Something shown when a home teleport starts and when it arrives.
 *
 * <p>Implementations must be safe to call for a player who disconnects mid-effect and must clean up
 * anything they spawned. {@link #shutdown()} is the last-chance sweep: an implementation that
 * spawns entities and does not remove them there leaves ghosts behind on every reload.
 */
public interface TeleportEffect {

    /** Plays where the player currently stands, just before the teleport. */
    void playEntry(Player player, EffectSettings settings);

    /** Plays at the destination, right after the player arrives. */
    void playArrival(Player player, Location destination, EffectSettings settings);

    /**
     * Ticks the entry effect wants the teleport warmup held for, so it finishes before the player
     * leaves. Zero leaves the server's own warmup untouched.
     */
    default long warmupTicks(EffectSettings settings) {
        return settings.durationTicks();
    }

    /** Removes everything this effect still owns. Called on disable and on reload. */
    void shutdown();
}
