package me.juancayc.polaroidhomes.effect;

import me.juancayc.polaroidhomes.config.EffectSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Plays nothing.
 *
 * <p>An explicit no-op rather than a null check at every call site: the listener never has to ask
 * whether effects are on, and {@link #warmupTicks} returning zero is what leaves the server's own
 * teleport warmup exactly as the home provider configured it.
 */
public final class NoneEffect implements TeleportEffect {

    @Override
    public void playEntry(Player player, EffectSettings settings) {
        // Intentionally empty.
    }

    @Override
    public void playArrival(Player player, Location destination, EffectSettings settings) {
        // Intentionally empty.
    }

    @Override
    public long warmupTicks(EffectSettings settings) {
        return 0L;
    }

    @Override
    public void shutdown() {
        // Nothing to own, nothing to release.
    }
}
