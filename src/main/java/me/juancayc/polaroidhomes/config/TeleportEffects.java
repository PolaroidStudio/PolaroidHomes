package me.juancayc.polaroidhomes.config;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * The entry and arrival settings for one kind of teleport.
 *
 * <p>There are two: the home effect, which is the shipped one, and the {@code /tpa} effect, which
 * inherits from it. Inheritance is the point — an operator who wants both to look the same should
 * not have to keep two identical blocks in step, and on most servers they will want exactly that.
 */
public record TeleportEffects(EffectSettings entry, EffectSettings arrival) {

    /** Reads a section with no parent to fall back to. Used for the home effect. */
    public static TeleportEffects from(@Nullable ConfigurationSection section) {
        return new TeleportEffects(
                EffectSettings.from(child(section, "entry"), Particle.PORTAL, 2.0D),
                EffectSettings.from(child(section, "arrival"), Particle.END_ROD, 1.5D));
    }

    /**
     * Reads a section, taking every unset field from {@code inherited}.
     *
     * <p>Absent section, absent {@code entry}, or an {@code entry} that sets only {@code duration}
     * all resolve the same way: whatever was not written here is the inherited value. That is why
     * the fallback is threaded through field by field rather than checking the section for null and
     * returning the parent whole — a half-written block is the case an operator actually produces,
     * and the shipped defaults leaking into its unset half would be a surprise.
     */
    public static TeleportEffects from(@Nullable ConfigurationSection section,
                                       TeleportEffects inherited) {
        return new TeleportEffects(
                EffectSettings.inheriting(child(section, "entry"), inherited.entry()),
                EffectSettings.inheriting(child(section, "arrival"), inherited.arrival()));
    }

    private static @Nullable ConfigurationSection child(@Nullable ConfigurationSection parent,
                                                        String key) {
        return parent == null ? null : parent.getConfigurationSection(key);
    }
}
