package me.juancayc.polaroidhomes.config;

import org.bukkit.Particle;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One half of a catalog entry's effect: either the departure or the arrival.
 *
 * <p>{@code durationSeconds} is declared by the operator, not measured. Model Engine's
 * {@code AnimationHandler.playAnimation(...)} returns an animation property, not a length, and the
 * API exposes no duration getter at all. The only runtime signal is
 * {@code hasFinishedAllAnimations()}, which can only be polled after the fact. Declaring the value
 * is therefore the only way to know, before starting, how long to hold the player in the warmup.
 *
 * <p>There is deliberately no inheritance and no partial form any more. An earlier release let the
 * {@code tpa} block fill its unset fields from the home block field by field, which made sense when
 * there were exactly two blocks on the whole server. A catalog entry is a product an operator sells,
 * and a product assembled from another product's leftovers is one an operator cannot read at a
 * glance, so every entry declares everything it needs and anything missing is a parse error the
 * operator is told about.
 */
public record EffectSettings(String model,
                             String animation,
                             double durationSeconds,
                             @Nullable Particle particle,
                             int particleCount,
                             double particleRadius) {

    /** Duration clamp floor, so an entry declaring zero still gets one frame of effect. */
    public static final double MIN_DURATION = 0.0D;

    /** Builds the animation half of a catalog entry. */
    public static EffectSettings animation(String model, String animation, double durationSeconds) {
        return new EffectSettings(model, animation, Math.max(MIN_DURATION, durationSeconds),
                null, 0, 0.0D);
    }

    /**
     * Builds the particle half of a catalog entry.
     *
     * <p>Particles carry no duration of their own: the helix is drawn in one burst rather than
     * animated over time. The value is kept at zero so a particle entry contributes no warmup
     * stretch, which is the honest answer — there is nothing to wait for.
     */
    public static EffectSettings particle(Particle particle, int count, double radius) {
        return new EffectSettings("", "", 0.0D, particle, Math.max(0, count),
                Math.max(0.0D, radius));
    }

    /** Ticks this effect occupies, rounded up so a fractional second is never cut short. */
    public long durationTicks() {
        return (long) Math.ceil(durationSeconds * 20.0D);
    }

    public boolean hasModel() {
        return !model.isBlank() && !animation.isBlank();
    }

    /**
     * Resolves a particle name, or null when it names none.
     *
     * <p>Null rather than a silent fallback, because the caller is parsing a catalog entry an
     * operator will sell: substituting a different particle would ship a product that does not look
     * like what its name says, and the operator would never find out. The entry is rejected with a
     * message instead.
     */
    public static @Nullable Particle parseParticle(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Particle names are reshuffled between Minecraft releases, so a config that was valid
            // last version can name one that no longer exists.
            return null;
        }
    }
}
