package me.juancayc.polaroidhomes.config;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Reads a section, taking every field the section does not set from {@code inherited}.
     *
     * <p>The difference from {@link #from} is which values fill the gaps: there it is the shipped
     * defaults, here it is another block the operator already configured. That is what lets the
     * {@code tpa} block be written as only the one or two fields that differ from the home effect,
     * or left out entirely to mean "the same".
     *
     * <p>{@code isSet} rather than a sentinel default: a section that explicitly writes
     * {@code particle-count: 0} means zero, and a getter's default argument cannot tell that apart
     * from the key being absent.
     */
    public static EffectSettings inheriting(@Nullable ConfigurationSection section,
                                            EffectSettings inherited) {
        if (section == null) {
            return inherited;
        }
        return new EffectSettings(
                section.isSet("model") ? section.getString("model", inherited.model())
                        : inherited.model(),
                section.isSet("animation") ? section.getString("animation", inherited.animation())
                        : inherited.animation(),
                section.isSet("duration")
                        ? Math.max(0.0D, section.getDouble("duration", inherited.durationSeconds()))
                        : inherited.durationSeconds(),
                parseParticle(section.getString("particle"), inherited.particle()),
                section.isSet("particle-count")
                        ? Math.max(0, section.getInt("particle-count", inherited.particleCount()))
                        : inherited.particleCount(),
                section.isSet("particle-radius")
                        ? Math.max(0.0D, section.getDouble("particle-radius",
                                inherited.particleRadius()))
                        : inherited.particleRadius());
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
