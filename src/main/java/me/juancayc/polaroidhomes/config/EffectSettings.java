package me.juancayc.polaroidhomes.config;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * One half of the teleport effect: either the departure or the arrival.
 *
 * <p>{@code durationSeconds} is declared by the operator, not measured. Model Engine's
 * {@code AnimationHandler.playAnimation(...)} returns an animation property, not a length, and the
 * API exposes no duration getter at all. The only runtime signal is
 * {@code hasFinishedAllAnimations()}, which can only be polled after the fact. Declaring the value
 * is therefore the only way to know, before starting, how long to hold the player in the warmup.
 */
public record EffectSettings(String model,
                             String animation,
                             double durationSeconds,
                             Particle particle,
                             int particleCount,
                             double particleRadius) {

    public static EffectSettings from(ConfigurationSection section,
                                      Particle defaultParticle,
                                      double defaultDuration) {
        if (section == null) {
            return new EffectSettings("", "", defaultDuration, defaultParticle, 60, 1.0D);
        }
        return new EffectSettings(
                section.getString("model", ""),
                section.getString("animation", ""),
                Math.max(0.0D, section.getDouble("duration", defaultDuration)),
                parseParticle(section.getString("particle"), defaultParticle),
                Math.max(0, section.getInt("particle-count", 60)),
                Math.max(0.0D, section.getDouble("particle-radius", 1.0D)));
    }

    /** Ticks this effect occupies, rounded up so a fractional second is never cut short. */
    public long durationTicks() {
        return (long) Math.ceil(durationSeconds * 20.0D);
    }

    public boolean hasModel() {
        return !model.isBlank() && !animation.isBlank();
    }

    private static Particle parseParticle(String name, Particle fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Particle names are reshuffled between Minecraft releases, so a config that was valid
            // last version can name one that no longer exists. Falling back beats refusing to load.
            return fallback;
        }
    }
}
