package me.juancayc.polaroidhomes.effect;

import me.juancayc.polaroidhomes.config.EffectSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Vanilla particles drawn as a rising helix around the player. Renders every catalog entry in the
 * particle category.
 *
 * <p>No dependency, no entities, nothing to clean up, which is why a particle entry is always
 * available: there is no installation an operator can get wrong and no reason to hide one.
 *
 * <p>The helix is drawn in one burst rather than animated over time, so a particle entry asks for
 * no warmup at all. That is why {@code EffectSettings.particle} stores a zero duration rather than
 * a declared one — there is nothing for a warmup to wait for.
 */
public final class ParticleEffect implements TeleportEffect {

    /** Turns of the helix over the full height, chosen to read as a spiral rather than a column. */
    private static final double TURNS = 3.0D;

    /** Height the helix climbs, roughly a player plus a head. */
    private static final double HEIGHT = 2.2D;

    @Override
    public void playEntry(Player player, EffectSettings settings) {
        draw(player.getLocation(), settings);
    }

    @Override
    public void playArrival(Player player, Location destination, EffectSettings settings) {
        draw(destination, settings);
    }

    private static void draw(Location origin, EffectSettings settings) {
        World world = origin.getWorld();
        int count = settings.particleCount();
        if (world == null || count <= 0) {
            return;
        }
        double radius = settings.particleRadius();
        // A catalog entry always carries a resolved particle; the null check is what keeps a
        // hand-built settings value from reaching spawnParticle as a null registry key.
        if (settings.particle() == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double progress = i / (double) count;
            double angle = progress * TURNS * 2.0D * Math.PI;
            double x = origin.getX() + Math.cos(angle) * radius;
            double z = origin.getZ() + Math.sin(angle) * radius;
            double y = origin.getY() + progress * HEIGHT;
            // One particle per spawn call with zero offset: passing a count here instead would let
            // the client scatter them and lose the spiral entirely.
            world.spawnParticle(settings.particle(), x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public void shutdown() {
        // Particles are client-side and expire on their own.
    }
}
