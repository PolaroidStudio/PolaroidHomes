package me.juancayc.polaroidhomes.effect;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.BiConsumer;

/**
 * Fires the arrival effect once the player has actually reached the destination.
 *
 * <p>Neither backend has a post-teleport event, so the arrival has to be noticed rather than
 * listened for. The obvious approach — schedule one task for the next tick — is what shipped first,
 * and it is wrong for the case that matters most: a teleport across worlds is asynchronous and the
 * move often has not happened a tick later, so the effect is drawn around a player still standing at
 * the origin. There it is invisible, because the departure effect has just been drawn in that exact
 * spot.
 *
 * <p>So this polls instead: every tick, up to a short ceiling, until the player is somewhere other
 * than where the teleport started. Polling is honest about what is being detected and costs a
 * handful of comparisons on a player-initiated action.
 *
 * <p>The ceiling exists so a teleport that never moves the player — cancelled downstream, or a home
 * whose destination is where they already stand — retires the task instead of leaving it running for
 * the session.
 */
public final class ArrivalWatcher {

    /** Long enough for a cross-world teleport to land; short enough not to linger when none does. */
    private static final int MAX_TICKS = 60;

    private ArrivalWatcher() {
    }

    /**
     * Watches for {@code player} to leave {@code origin}, then hands the destination to {@code onArrival}.
     *
     * @param origin where the player stood when the teleport was committed to
     */
    public static void await(Plugin plugin, Player player, Location origin,
                             BiConsumer<Player, Location> onArrival) {
        BukkitTask[] handle = new BukkitTask[1];
        int[] ticks = {0};
        handle[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                handle[0].cancel();
                return;
            }
            Location now = player.getLocation();
            boolean movedWorld = now.getWorld() != null && origin.getWorld() != null
                    && !now.getWorld().equals(origin.getWorld());
            // A square distance, not distance(): the comparison is against a constant and the square
            // root is the expensive half of it. One block is well below any home-to-home hop and well
            // above the drift of standing still.
            boolean movedFar = !movedWorld && now.getWorld() == origin.getWorld()
                    && now.distanceSquared(origin) > 1.0D;
            if (movedWorld || movedFar) {
                handle[0].cancel();
                onArrival.accept(player, now);
                return;
            }
            if (++ticks[0] >= MAX_TICKS) {
                // Never moved. Drawing the arrival where they already are would be a second copy of
                // the departure effect, so nothing is drawn.
                handle[0].cancel();
            }
        }, 1L, 1L);
    }
}
