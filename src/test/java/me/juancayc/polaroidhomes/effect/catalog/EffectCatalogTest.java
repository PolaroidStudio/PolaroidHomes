package me.juancayc.polaroidhomes.effect.catalog;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how a catalog is read out of config.yml.
 *
 * <p>The promise this file keeps is that one malformed entry costs exactly one effect. Everything
 * else in the catalog still loads, the operator is told which entry and which field, and nothing is
 * quietly substituted — an entry that named a particle this version does not have must not come
 * back as a different particle, because the product would then not match what a player bought.
 */
class EffectCatalogTest {

    private static ConfigurationSection read(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml))
                .getConfigurationSection("teleport-effects");
    }

    private static EffectCatalog catalog(String yaml) {
        return EffectCatalog.from(read(yaml));
    }

    // --- Both categories parse ------------------------------------------------

    @Test
    void anAnimationEntryCarriesItsModelAnimationAndDuration() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  animations:
                    purple_charge:
                      display-name: "<#cd80f7>Purple"
                      icon: AMETHYST_SHARD
                      entry:
                        model: ac_vfx_teleport_charge_purple
                        animation: teleport_charge_origin
                        duration: 3.0
                      arrival:
                        model: ac_vfx_teleport_charge_purple
                        animation: teleport_charge_destination
                        duration: 2.5
                """);

        assertEquals(List.of(), catalog.problems());
        EffectEntry entry = catalog.find(EffectCategory.ANIMATION, "purple_charge");
        assertNotNull(entry);
        assertEquals("<#cd80f7>Purple", entry.displayName());
        assertEquals("AMETHYST_SHARD", entry.icon());
        assertEquals("ac_vfx_teleport_charge_purple", entry.entry().model());
        assertEquals("teleport_charge_origin", entry.entry().animation());
        assertEquals(3.0D, entry.entry().durationSeconds());
        assertEquals("teleport_charge_destination", entry.arrival().animation());
        assertEquals(2.5D, entry.arrival().durationSeconds());
    }

    @Test
    void aParticleEntryCarriesItsParticleCountAndRadius() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    portal:
                      display-name: "Portal"
                      icon: ENDER_PEARL
                      entry:
                        particle: PORTAL
                        count: 120
                        radius: 1.5
                      arrival:
                        particle: END_ROD
                        count: 80
                        radius: 1.0
                """);

        assertEquals(List.of(), catalog.problems());
        EffectEntry entry = catalog.find(EffectCategory.PARTICLE, "portal");
        assertNotNull(entry);
        assertEquals(Particle.PORTAL, entry.entry().particle());
        assertEquals(120, entry.entry().particleCount());
        assertEquals(1.5D, entry.entry().particleRadius());
        assertEquals(Particle.END_ROD, entry.arrival().particle());
        assertEquals(80, entry.arrival().particleCount());
    }

    @Test
    void bothCategoriesLoadFromOneFile() {
        EffectCatalog catalog = catalog(BOTH);

        assertEquals(2, catalog.size());
        assertEquals(1, catalog.byCategory(EffectCategory.ANIMATION).size());
        assertEquals(1, catalog.byCategory(EffectCategory.PARTICLE).size());
    }

    /** Declaration order is the order the menu pages through, so it is part of the contract. */
    @Test
    void entriesKeepTheOrderTheOperatorWroteThem() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    zebra:
                      display-name: "Z"
                      icon: STONE
                      entry:
                        particle: FLAME
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                    alpha:
                      display-name: "A"
                      icon: STONE
                      entry:
                        particle: FLAME
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                """);

        List<EffectEntry> entries = catalog.byCategory(EffectCategory.PARTICLE);
        assertEquals("zebra", entries.get(0).id());
        assertEquals("alpha", entries.get(1).id());
    }

    // --- Malformed entries ----------------------------------------------------

    @Test
    void anUnknownParticleRejectsTheEntryRatherThanSubstitutingAnother() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    broken:
                      display-name: "Broken"
                      icon: STONE
                      entry:
                        particle: NOT_A_REAL_PARTICLE
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                """);

        assertTrue(catalog.isEmpty());
        assertEquals(1, catalog.problems().size());
        assertTrue(catalog.problems().get(0).contains("NOT_A_REAL_PARTICLE"));
    }

    @Test
    void anAnimationWithoutAnAnimationIdIsRejected() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  animations:
                    halfway:
                      display-name: "Halfway"
                      icon: STONE
                      entry:
                        model: some_model
                        duration: 1.0
                      arrival:
                        model: some_model
                        animation: landing
                        duration: 1.0
                """);

        assertTrue(catalog.isEmpty());
        assertTrue(catalog.problems().get(0).contains("animation"));
    }

    @Test
    void anAnimationWithoutADurationIsRejectedBecauseItCannotBeMeasured() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  animations:
                    timeless:
                      display-name: "Timeless"
                      icon: STONE
                      entry:
                        model: m
                        animation: a
                      arrival:
                        model: m
                        animation: b
                        duration: 1.0
                """);

        assertTrue(catalog.isEmpty());
        assertTrue(catalog.problems().get(0).contains("duration"));
    }

    @Test
    void anEntryMissingItsArrivalHalfIsRejected() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    departure_only:
                      display-name: "One way"
                      icon: STONE
                      entry:
                        particle: FLAME
                        count: 10
                """);

        assertTrue(catalog.isEmpty());
        assertTrue(catalog.problems().get(0).contains("arrival"));
    }

    @Test
    void anEntryWithoutADisplayNameIsRejected() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    nameless:
                      icon: STONE
                      entry:
                        particle: FLAME
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                """);

        assertTrue(catalog.isEmpty());
        assertTrue(catalog.problems().get(0).contains("display-name"));
    }

    @Test
    void anEntryWithoutAnIconIsRejected() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    iconless:
                      display-name: "No icon"
                      entry:
                        particle: FLAME
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                """);

        assertTrue(catalog.isEmpty());
        assertTrue(catalog.problems().get(0).contains("icon"));
    }

    @Test
    void aZeroCountIsRejectedBecauseItWouldDrawNothing() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    invisible:
                      display-name: "Invisible"
                      icon: STONE
                      entry:
                        particle: FLAME
                        count: 0
                      arrival:
                        particle: FLAME
                        count: 10
                """);

        assertTrue(catalog.isEmpty());
        assertTrue(catalog.problems().get(0).contains("count"));
    }

    /** One bad entry must not take the whole catalog with it. */
    @Test
    void aMalformedEntryCostsOnlyItself() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    good:
                      display-name: "Good"
                      icon: STONE
                      entry:
                        particle: FLAME
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                    bad:
                      display-name: "Bad"
                      icon: STONE
                      entry:
                        particle: NOPE
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                """);

        assertEquals(1, catalog.size());
        assertNotNull(catalog.find(EffectCategory.PARTICLE, "good"));
        assertNull(catalog.find(EffectCategory.PARTICLE, "bad"));
        assertEquals(1, catalog.problems().size());
    }

    // --- Ids ------------------------------------------------------------------

    @Test
    void anIdWithCharactersAPermissionNodeCannotCarryIsRejected() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    "my effect":
                      display-name: "Spaces"
                      icon: STONE
                      entry:
                        particle: FLAME
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                """);

        assertTrue(catalog.isEmpty());
        assertTrue(catalog.problems().get(0).contains("permission node"));
    }

    @Test
    void anIdIsFoldedToLowercaseSoTheFileAndAStoredRowAgree() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    Portal:
                      display-name: "Portal"
                      icon: STONE
                      entry:
                        particle: FLAME
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                """);

        EffectEntry entry = catalog.find(EffectCategory.PARTICLE, "portal");
        assertNotNull(entry);
        assertEquals("portal", entry.id());
        // And the lookup folds the other way too, so a row written in either spelling resolves.
        assertNotNull(catalog.find(EffectCategory.PARTICLE, "PORTAL"));
    }

    // --- Permission generation ------------------------------------------------

    @Test
    void thePermissionNodeIsGeneratedFromTheCategoryAndTheId() {
        EffectCatalog catalog = catalog(BOTH);

        assertEquals("polaroidhomes.animation.purple_charge",
                catalog.find(EffectCategory.ANIMATION, "purple_charge").permission());
        assertEquals("polaroidhomes.particle.portal",
                catalog.find(EffectCategory.PARTICLE, "portal").permission());
    }

    /**
     * The two categories share an id namespace only by accident of looking alike; they must not
     * collide, because the nodes an operator sells are distinct.
     */
    @Test
    void oneIdInBothCategoriesIsTwoDifferentProducts() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  animations:
                    shared:
                      display-name: "A"
                      icon: STONE
                      entry:
                        model: m
                        animation: a
                        duration: 1.0
                      arrival:
                        model: m
                        animation: b
                        duration: 1.0
                  particles:
                    shared:
                      display-name: "P"
                      icon: STONE
                      entry:
                        particle: FLAME
                        count: 10
                      arrival:
                        particle: FLAME
                        count: 10
                """);

        assertEquals(2, catalog.size());
        assertEquals("polaroidhomes.animation.shared",
                catalog.find(EffectCategory.ANIMATION, "shared").permission());
        assertEquals("polaroidhomes.particle.shared",
                catalog.find(EffectCategory.PARTICLE, "shared").permission());
    }

    // --- Availability ---------------------------------------------------------

    @Test
    void anAnimationIsUnavailableWithoutModelEngineAndAParticleIsAlwaysAvailable() {
        EffectCatalog catalog = catalog(BOTH);

        assertTrue(catalog.available(EffectCategory.ANIMATION, false).isEmpty());
        assertEquals(1, catalog.available(EffectCategory.ANIMATION, true).size());
        assertEquals(1, catalog.available(EffectCategory.PARTICLE, false).size());
        assertEquals(1, catalog.available(EffectCategory.PARTICLE, true).size());
    }

    // --- Absent sections ------------------------------------------------------

    @Test
    void anAbsentSectionIsAnEmptyCatalogRatherThanAnError() {
        assertTrue(EffectCatalog.from(null).isEmpty());
        assertEquals(List.of(), EffectCatalog.from(null).problems());

        EffectCatalog onlyFlags = catalog("""
                teleport-effects:
                  homes: true
                  tpa: false
                """);
        assertTrue(onlyFlags.isEmpty());
        assertEquals(List.of(), onlyFlags.problems());
    }

    @Test
    void anEntryThatIsNotASectionIsReportedRatherThanCrashing() {
        EffectCatalog catalog = catalog("""
                teleport-effects:
                  particles:
                    scalar: "not a section"
                """);

        assertTrue(catalog.isEmpty());
        assertFalse(catalog.problems().isEmpty());
    }


    // --- The shipped file -----------------------------------------------------

    /**
     * The example catalog in the jar's own config.yml has to be a working one.
     *
     * <p>It is the first thing every operator reads and the thing most of them edit rather than
     * replace, so an entry in it that does not parse ships a broken example to every new install
     * and teaches the wrong shape.
     */
    @Test
    void theShippedConfigDeclaresAWorkingCatalog() {
        java.io.InputStream in = EffectCatalogTest.class.getResourceAsStream("/config.yml");
        assertNotNull(in, "config.yml must be on the test classpath");
        ConfigurationSection effects = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                .getConfigurationSection("teleport-effects");

        EffectCatalog catalog = EffectCatalog.from(effects);

        assertEquals(List.of(), catalog.problems(),
                "the shipped example catalog must parse without complaint");
        assertFalse(catalog.byCategory(EffectCategory.ANIMATION).isEmpty(),
                "the example must show an animation entry, since that is the harder shape");
        assertFalse(catalog.byCategory(EffectCategory.PARTICLE).isEmpty());
    }

    private static final String BOTH = """
            teleport-effects:
              animations:
                purple_charge:
                  display-name: "Purple"
                  icon: AMETHYST_SHARD
                  entry:
                    model: m
                    animation: origin
                    duration: 3.0
                  arrival:
                    model: m
                    animation: destination
                    duration: 3.0
              particles:
                portal:
                  display-name: "Portal"
                  icon: ENDER_PEARL
                  entry:
                    particle: PORTAL
                    count: 120
                    radius: 1.0
                  arrival:
                    particle: END_ROD
                    count: 80
                    radius: 1.0
            """;
}
