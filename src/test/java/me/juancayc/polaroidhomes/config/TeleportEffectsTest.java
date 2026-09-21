package me.juancayc.polaroidhomes.config;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the inheritance rule for the tpa effect block.
 *
 * <p>The promise the config comments make is precise: anything the tpa block does not set is taken
 * from the home block, field by field. A whole-block fallback would satisfy the absent case and
 * quietly break the partial one, which is the case operators actually produce, so both are pinned
 * here along with the values the shipped defaults must not leak into.
 */
class TeleportEffectsTest {

    private static final String HOME = """
            teleport-effects:
              entry:
                model: home_model
                animation: home_out
                duration: 4.0
                particle: FLAME
                particle-count: 33
                particle-radius: 2.5
              arrival:
                model: home_model
                animation: home_in
                duration: 3.0
                particle: CLOUD
                particle-count: 22
                particle-radius: 1.5
            """;

    private static ConfigurationSection effects(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml))
                .getConfigurationSection("teleport-effects");
    }

    private static TeleportEffects home() {
        return TeleportEffects.from(effects(HOME));
    }

    private static TeleportEffects tpa(String tpaBlock) {
        ConfigurationSection section = effects(HOME + tpaBlock);
        return TeleportEffects.from(section.getConfigurationSection("tpa"), home());
    }

    // --- The home block still reads as it always did --------------------------

    @Test
    void theHomeBlockIsReadFromItsOwnSection() {
        TeleportEffects home = home();

        assertEquals("home_out", home.entry().animation());
        assertEquals(4.0D, home.entry().durationSeconds());
        assertEquals(Particle.FLAME, home.entry().particle());
        assertEquals(Particle.CLOUD, home.arrival().particle());
    }

    /** An absent home block falls back to the shipped defaults, not to nothing. */
    @Test
    void anAbsentHomeBlockUsesTheShippedDefaults() {
        TeleportEffects home = TeleportEffects.from(null);

        assertEquals(Particle.PORTAL, home.entry().particle());
        assertEquals(2.0D, home.entry().durationSeconds());
        assertEquals(Particle.END_ROD, home.arrival().particle());
        assertEquals(1.5D, home.arrival().durationSeconds());
    }

    // --- tpa section absent ---------------------------------------------------

    /**
     * The case an operator gets by deleting the block, and the one the config comment promises:
     * a tpa then looks exactly like a home teleport.
     */
    @Test
    void anAbsentTpaSectionIsTheHomeEffectExactly() {
        TeleportEffects home = home();
        TeleportEffects tpa = TeleportEffects.from(null, home);

        assertEquals(home.entry(), tpa.entry());
        assertEquals(home.arrival(), tpa.arrival());
    }

    /** An empty tpa block, and one holding only `enabled`, are the same as no block at all. */
    @Test
    void aTpaSectionWithNoEntryOrArrivalIsTheHomeEffect() {
        TeleportEffects home = home();
        TeleportEffects tpa = tpa("""
              tpa:
                enabled: true
            """);

        assertEquals(home.entry(), tpa.entry());
        assertEquals(home.arrival(), tpa.arrival());
    }

    /** Inheriting from an absent section hands back the parent itself, not a rebuilt copy. */
    @Test
    void inheritingFromAnAbsentSectionReturnsTheParentSettings() {
        EffectSettings parent = home().entry();

        assertSame(parent, EffectSettings.inheriting(null, parent));
    }

    // --- tpa section partially set --------------------------------------------

    /**
     * The case that a whole-block fallback would get wrong: one field set, every other one still
     * the home value rather than the shipped default.
     */
    @Test
    void anUnsetFieldTakesTheHomeValueNotTheShippedDefault() {
        TeleportEffects tpa = tpa("""
              tpa:
                entry:
                  particle: DRAGON_BREATH
            """);

        assertEquals(Particle.DRAGON_BREATH, tpa.entry().particle());
        // Everything else is the home entry, not PORTAL / 2.0 / 60 / 1.0.
        assertEquals("home_model", tpa.entry().model());
        assertEquals("home_out", tpa.entry().animation());
        assertEquals(4.0D, tpa.entry().durationSeconds());
        assertEquals(33, tpa.entry().particleCount());
        assertEquals(2.5D, tpa.entry().particleRadius());
    }

    /** entry and arrival inherit independently: setting one must not disturb the other. */
    @Test
    void settingOnlyEntryLeavesArrivalOnTheHomeValues() {
        TeleportEffects home = home();
        TeleportEffects tpa = tpa("""
              tpa:
                entry:
                  duration: 0.5
            """);

        assertEquals(0.5D, tpa.entry().durationSeconds());
        assertEquals(home.arrival(), tpa.arrival());
    }

    @Test
    void settingOnlyArrivalLeavesEntryOnTheHomeValues() {
        TeleportEffects home = home();
        TeleportEffects tpa = tpa("""
              tpa:
                arrival:
                  particle-count: 5
            """);

        assertEquals(home.entry(), tpa.entry());
        assertEquals(5, tpa.arrival().particleCount());
        assertEquals("home_in", tpa.arrival().animation());
    }

    /**
     * An explicit zero is a value, not an absent key. A getter default cannot tell those apart,
     * which is why the resolution asks isSet first; without that, an operator switching the tpa
     * particles off would silently get the home count instead.
     */
    @Test
    void anExplicitZeroIsHonouredRatherThanTreatedAsUnset() {
        TeleportEffects tpa = tpa("""
              tpa:
                entry:
                  particle-count: 0
                  particle-radius: 0.0
                  duration: 0.0
            """);

        assertEquals(0, tpa.entry().particleCount());
        assertEquals(0.0D, tpa.entry().particleRadius());
        assertEquals(0.0D, tpa.entry().durationSeconds());
    }

    /** An explicitly emptied model turns the Model Engine half off without touching the rest. */
    @Test
    void anExplicitlyEmptyModelIsHonoured() {
        TeleportEffects tpa = tpa("""
              tpa:
                entry:
                  model: ''
            """);

        assertEquals("", tpa.entry().model());
        assertFalse(tpa.entry().hasModel());
        assertEquals(33, tpa.entry().particleCount());
    }

    /** A negative duration is clamped on the tpa path exactly as it is on the home path. */
    @Test
    void aNegativeValueIsClampedNotInherited() {
        TeleportEffects tpa = tpa("""
              tpa:
                entry:
                  duration: -3.0
                  particle-count: -10
            """);

        assertEquals(0.0D, tpa.entry().durationSeconds());
        assertEquals(0, tpa.entry().particleCount());
    }

    /** A particle name that no longer exists falls back to the home one, never to PORTAL. */
    @Test
    void anUnknownParticleFallsBackToTheHomeParticle() {
        TeleportEffects tpa = tpa("""
              tpa:
                entry:
                  particle: NO_SUCH_PARTICLE_EVER
            """);

        assertEquals(Particle.FLAME, tpa.entry().particle());
    }

    // --- tpa section fully set ------------------------------------------------

    /** Nothing is inherited when everything is written, and the home block is left untouched. */
    @Test
    void aFullySetTpaSectionInheritsNothing() {
        TeleportEffects home = home();
        TeleportEffects tpa = tpa("""
              tpa:
                entry:
                  model: tpa_model
                  animation: tpa_out
                  duration: 1.25
                  particle: SOUL
                  particle-count: 7
                  particle-radius: 0.25
                arrival:
                  model: tpa_model
                  animation: tpa_in
                  duration: 0.75
                  particle: HEART
                  particle-count: 9
                  particle-radius: 0.5
            """);

        assertEquals(new EffectSettings("tpa_model", "tpa_out", 1.25D, Particle.SOUL, 7, 0.25D),
                tpa.entry());
        assertEquals(new EffectSettings("tpa_model", "tpa_in", 0.75D, Particle.HEART, 9, 0.5D),
                tpa.arrival());
        assertTrue(tpa.entry().hasModel());

        assertEquals("home_out", home.entry().animation());
        assertEquals(Particle.FLAME, home.entry().particle());
    }

    /** Inheritance is one level deep: a tpa block resolved against shipped defaults still works. */
    @Test
    void aTpaSectionInheritingFromTheShippedDefaultsResolves() {
        TeleportEffects tpa = TeleportEffects.from(
                effects("""
                        teleport-effects:
                          tpa:
                            entry:
                              particle-count: 11
                        """).getConfigurationSection("tpa"),
                TeleportEffects.from(null));

        assertEquals(11, tpa.entry().particleCount());
        assertEquals(Particle.PORTAL, tpa.entry().particle());
        assertEquals(2.0D, tpa.entry().durationSeconds());
    }
}
