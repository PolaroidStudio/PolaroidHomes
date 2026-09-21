package me.juancayc.polaroidhomes.config.migration;

import me.juancayc.polaroidhomes.effect.catalog.EffectCatalog;
import me.juancayc.polaroidhomes.effect.catalog.EffectCategory;
import me.juancayc.polaroidhomes.effect.catalog.EffectEntry;
import org.bukkit.Particle;
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
 * The v5 -> v6 step, which is the one migration in this plugin that would destroy real
 * configuration if it did nothing.
 *
 * <p>An existing server has a working global effect written in keys version 6 deletes. This file
 * pins that the values are read out before those keys go, that the result is a catalog entry the
 * plugin can actually parse, and that a server with effects switched off does not get a product it
 * never had.
 */
class GlobalEffectMigrationTest {

    private static YamlConfiguration config(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    private static EffectCatalog catalogOf(YamlConfiguration config) {
        return EffectCatalog.from(config.getConfigurationSection("teleport-effects"));
    }

    /** The user's own server: model-engine mode with a real purple charge model. */
    private static final String MODEL_ENGINE_SERVER = """
            config-version: 5
            teleport-effects:
              mode: model-engine
              fallback: particles
              entry:
                model: ac_vfx_teleport_charge_purple
                animation: teleport_charge_origin
                duration: 3.0
                particle: PORTAL
                particle-count: 120
                particle-radius: 1.0
              arrival:
                model: ac_vfx_teleport_charge_purple
                animation: teleport_charge_destination
                duration: 2.5
                particle: END_ROD
                particle-count: 80
                particle-radius: 1.0
              tpa:
                enabled: true
                entry:
                  particle: DRAGON_BREATH
              max-effect-seconds: 10.0
            """;

    private static final String PARTICLE_SERVER = """
            config-version: 5
            teleport-effects:
              mode: particles
              fallback: particles
              entry:
                model: polaroid_warp
                animation: warp_out
                duration: 2.0
                particle: FLAME
                particle-count: 33
                particle-radius: 2.5
              arrival:
                model: polaroid_warp
                animation: warp_in
                duration: 1.5
                particle: CLOUD
                particle-count: 22
                particle-radius: 1.5
              tpa:
                enabled: false
              max-effect-seconds: 10.0
            """;

    // --- The values survive ---------------------------------------------------

    @Test
    void aModelEngineServerKeepsItsModelAnimationsAndDurations() {
        YamlConfiguration file = config(MODEL_ENGINE_SERVER);

        ConfigMigrations.globalEffectToCatalog().apply(file);

        EffectEntry entry = catalogOf(file).find(EffectCategory.ANIMATION, "legacy_effect");
        assertNotNull(entry, "the operator's working effect must survive as a catalog entry");
        assertEquals("ac_vfx_teleport_charge_purple", entry.entry().model());
        assertEquals("teleport_charge_origin", entry.entry().animation());
        assertEquals(3.0D, entry.entry().durationSeconds());
        assertEquals("ac_vfx_teleport_charge_purple", entry.arrival().model());
        assertEquals("teleport_charge_destination", entry.arrival().animation());
        assertEquals(2.5D, entry.arrival().durationSeconds());
    }

    @Test
    void aParticleServerKeepsItsParticlesCountsAndRadii() {
        YamlConfiguration file = config(PARTICLE_SERVER);

        ConfigMigrations.globalEffectToCatalog().apply(file);

        EffectEntry entry = catalogOf(file).find(EffectCategory.PARTICLE, "legacy_effect");
        assertNotNull(entry);
        assertEquals(Particle.FLAME, entry.entry().particle());
        assertEquals(33, entry.entry().particleCount());
        assertEquals(2.5D, entry.entry().particleRadius());
        assertEquals(Particle.CLOUD, entry.arrival().particle());
        assertEquals(22, entry.arrival().particleCount());
        assertEquals(1.5D, entry.arrival().particleRadius());
    }

    /**
     * The whole point: whatever the migration writes has to be something the live parser reads back
     * without complaint, or the operator's effect survives into a file the plugin then rejects.
     */
    @Test
    void whatTheMigrationWritesParsesCleanly() {
        for (String yaml : List.of(MODEL_ENGINE_SERVER, PARTICLE_SERVER)) {
            YamlConfiguration file = config(yaml);

            ConfigMigrations.globalEffectToCatalog().apply(file);

            EffectCatalog catalog = catalogOf(file);
            assertEquals(List.of(), catalog.problems(),
                    "a migrated file must not produce parse problems");
            assertEquals(1, catalog.size());
        }
    }

    @Test
    void theMigratedEntryGeneratesTheNodeAnOperatorHasToGrant() {
        YamlConfiguration file = config(MODEL_ENGINE_SERVER);

        ConfigMigrations.globalEffectToCatalog().apply(file);

        assertEquals("polaroidhomes.animation.legacy_effect",
                catalogOf(file).find(EffectCategory.ANIMATION, "legacy_effect").permission());
    }

    /** An entry needs both of these to parse, and neither existed before version 6. */
    @Test
    void theMigratedEntryIsGivenADisplayNameAndAnIcon() {
        YamlConfiguration file = config(MODEL_ENGINE_SERVER);

        ConfigMigrations.globalEffectToCatalog().apply(file);

        EffectEntry entry = catalogOf(file).find(EffectCategory.ANIMATION, "legacy_effect");
        assertFalse(entry.displayName().isBlank());
        assertFalse(entry.icon().isBlank());
    }

    // --- The old keys go ------------------------------------------------------

    @Test
    void theSupersededKeysAreRemovedAfterBeingRead() {
        YamlConfiguration file = config(MODEL_ENGINE_SERVER);

        ConfigMigrations.globalEffectToCatalog().apply(file);

        assertFalse(file.contains("teleport-effects.mode"));
        assertFalse(file.contains("teleport-effects.fallback"));
        assertFalse(file.contains("teleport-effects.entry"));
        assertFalse(file.contains("teleport-effects.arrival"));
        assertFalse(file.contains("teleport-effects.tpa.enabled"));
        assertFalse(file.contains("teleport-effects.tpa.entry"));
    }

    /** The one key in this block that was never superseded. */
    @Test
    void maxEffectSecondsIsLeftAlone() {
        YamlConfiguration file = config(MODEL_ENGINE_SERVER);

        ConfigMigrations.globalEffectToCatalog().apply(file);

        assertEquals(10.0D, file.getDouble("teleport-effects.max-effect-seconds"));
    }

    // --- The new switches -----------------------------------------------------

    @Test
    void homeEffectsStayOnBecauseThatIsWhatTheServerAlreadyDid() {
        YamlConfiguration file = config(MODEL_ENGINE_SERVER);

        ConfigMigrations.globalEffectToCatalog().apply(file);

        assertTrue(file.getBoolean("teleport-effects.homes"));
    }

    @Test
    void theTpaSwitchCarriesAcrossWhateverTheOperatorHadSet() {
        YamlConfiguration on = config(MODEL_ENGINE_SERVER);
        ConfigMigrations.globalEffectToCatalog().apply(on);
        assertTrue(on.getBoolean("teleport-effects.tpa"),
                "an operator who turned tpa effects on keeps them on");

        YamlConfiguration off = config(PARTICLE_SERVER);
        ConfigMigrations.globalEffectToCatalog().apply(off);
        assertFalse(off.getBoolean("teleport-effects.tpa"),
                "an operator who left tpa effects off keeps them off");
    }

    // --- Nothing was playing --------------------------------------------------

    @Test
    void aServerWithEffectsOffGetsNoCatalogEntry() {
        YamlConfiguration file = config("""
                config-version: 5
                teleport-effects:
                  mode: none
                  entry:
                    model: polaroid_warp
                    animation: warp_out
                    duration: 2.0
                  arrival:
                    model: polaroid_warp
                    animation: warp_in
                    duration: 1.5
                """);

        ConfigMigrations.globalEffectToCatalog().apply(file);

        // There was nothing to see, so there is nothing to preserve. Manufacturing a product from
        // a switched-off block would put an entry in a shop the operator never chose to sell.
        assertTrue(catalogOf(file).isEmpty());
    }

    @Test
    void aModelEngineModeWithNoModelDeclaredProducesNothingRatherThanABrokenEntry() {
        YamlConfiguration file = config("""
                config-version: 5
                teleport-effects:
                  mode: model-engine
                  entry:
                    duration: 2.0
                  arrival:
                    duration: 1.5
                """);

        ConfigMigrations.globalEffectToCatalog().apply(file);

        assertNull(catalogOf(file).find(EffectCategory.ANIMATION, "legacy_effect"));
        assertEquals(List.of(), catalogOf(file).problems());
    }

    /** The old parser accepted several spellings, so the migration has to as well. */
    @Test
    void theModeIsReadTheSameWayTheOldParserReadIt() {
        for (String spelling : List.of("model-engine", "model_engine", "MODELENGINE",
                "  Model-Engine  ")) {
            YamlConfiguration file = config("""
                    config-version: 5
                    teleport-effects:
                      mode: "%s"
                      entry:
                        model: m
                        animation: out
                        duration: 1.0
                      arrival:
                        model: m
                        animation: in
                        duration: 1.0
                    """.formatted(spelling));

            ConfigMigrations.globalEffectToCatalog().apply(file);

            assertNotNull(catalogOf(file).find(EffectCategory.ANIMATION, "legacy_effect"),
                    spelling + " should migrate as an animation");
        }
    }

    // --- menu.yml -------------------------------------------------------------

    @Test
    void theEffectsButtonIsAddedToAnUntouchedShippedGrid() {
        YamlConfiguration menu = config("""
                config-version: 1
                homes:
                  rows:
                    - "HHHHHHHHH"
                    - "HHHHHHHHH"
                    - "HHHHHHHHH"
                    - "HHHHHHHHH"
                    - "HHHHHHHHH"
                    - "<###I###>"
                """);

        ConfigMigrations.addEffectsButtonOnUpgrade().apply(menu);

        assertEquals("<##EI###>", menu.getStringList("homes.rows").get(5));
    }

    /**
     * A customised grid is left exactly as the operator wrote it. Hunting for a filler slot to
     * overwrite would move a button somebody placed deliberately, on a release they upgraded for
     * something else entirely.
     */
    @Test
    void aCustomisedGridIsNotRewritten() {
        List<String> custom = List.of(
                "#########",
                "#HHHHHHH#",
                "#HHHHHHH#",
                "<###I###>");
        YamlConfiguration menu = config("config-version: 1\n");
        menu.set("homes.rows", custom);

        ConfigMigrations.addEffectsButtonOnUpgrade().apply(menu);

        assertEquals(custom, menu.getStringList("homes.rows"));
    }

    @Test
    void aMenuFileWithNoHomesSectionIsLeftAlone() {
        YamlConfiguration menu = config("config-version: 1\n");

        ConfigMigrations.addEffectsButtonOnUpgrade().apply(menu);

        assertTrue(menu.getStringList("homes.rows").isEmpty());
    }
}
