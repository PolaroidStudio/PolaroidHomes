package me.juancayc.polaroidhomes.effect;

import me.juancayc.polaroidhomes.config.EffectSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Something shown when a home teleport starts and when it arrives.
 *
 * <p>One implementation per catalog category: the renderer is chosen from the entry a player has
 * equipped, not from a server-wide mode, so both live at once and either may be asked to play at
 * any moment.
 *
 * <p>Implementations must be safe to call for a player who disconnects mid-effect and must clean up
 * anything they spawned. {@link #shutdown()} is the last-chance sweep: an implementation that
 * spawns entities and does not remove them there leaves ghosts behind on every reload.
 *
 * <p>How long the warmup is held for is NOT asked here. It comes from the equipped entry's declared
 * entry duration, which {@code EffectPlayer} reads directly — a renderer has no opinion about it.
 */
public interface TeleportEffect {

    /** Plays where the player currently stands, just before the teleport. */
    void playEntry(Player player, EffectSettings settings);

    /** Plays at the destination, right after the player arrives. */
    void playArrival(Player player, Location destination, EffectSettings settings);

    /** Removes everything this effect still owns. Called on disable and on reload. */
    void shutdown();
}
