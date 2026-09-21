package me.juancayc.polaroidhomes.effect.catalog;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The equip state machine, and the two rules that would otherwise only reveal themselves on a live
 * server months later.
 *
 * <p>"Equipping a particle unequips an animation" is invisible until a player owns one of each.
 * "Losing a permission keeps the row but stops the effect" is invisible until somebody's rank
 * lapses and then comes back. Both run here on every build instead.
 */
class EffectSelectionTest {

    private static final String CATALOG = """
            teleport-effects:
              animations:
                purple_charge:
                  display-name: "Purple"
                  icon: STONE
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
                  icon: STONE
                  entry:
                    particle: PORTAL
                    count: 120
                  arrival:
                    particle: END_ROD
                    count: 80
            """;

    private static final EffectCatalog CATALOG_VALUE = EffectCatalog.from(
            YamlConfiguration.loadConfiguration(new StringReader(CATALOG))
                    .getConfigurationSection("teleport-effects"));

    private static final EffectEntry ANIMATION =
            CATALOG_VALUE.find(EffectCategory.ANIMATION, "purple_charge");
    private static final EffectEntry PARTICLE =
            CATALOG_VALUE.find(EffectCategory.PARTICLE, "portal");

    /** A player holding exactly the listed nodes, which is what a permissions plugin resolves to. */
    private static Predicate<String> holding(String... nodes) {
        Set<String> held = Set.of(nodes);
        return held::contains;
    }

    private static final Predicate<String> HOLDS_NOTHING = node -> false;
    private static final Predicate<String> HOLDS_EVERYTHING = node -> true;

    // --- Cross-category replacement -------------------------------------------

    @Test
    void equippingAParticleReplacesAnEquippedAnimation() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.ANIMATION, "purple_charge");

        EquippedEffect next = EffectSelection.toggle(worn, PARTICLE);

        assertNotNull(next);
        assertEquals(EffectCategory.PARTICLE, next.category());
        assertEquals("portal", next.id());
    }

    @Test
    void equippingAnAnimationReplacesAnEquippedParticle() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.PARTICLE, "portal");

        EquippedEffect next = EffectSelection.toggle(worn, ANIMATION);

        assertNotNull(next);
        assertEquals(EffectCategory.ANIMATION, next.category());
        assertEquals("purple_charge", next.id());
    }

    @Test
    void equippingWithNothingOnJustEquipsIt() {
        EquippedEffect next = EffectSelection.toggle(null, PARTICLE);

        assertNotNull(next);
        assertEquals("portal", next.id());
    }

    @Test
    void clickingTheEquippedEntryTakesItOff() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.PARTICLE, "portal");

        assertNull(EffectSelection.toggle(worn, PARTICLE));
    }

    /** Same id, other category: a different product, so it equips rather than unequipping. */
    @Test
    void anEntryWithTheSameIdInTheOtherCategoryDoesNotCountAsTheEquippedOne() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.ANIMATION, "portal");

        EquippedEffect next = EffectSelection.toggle(worn, PARTICLE);

        assertNotNull(next);
        assertEquals(EffectCategory.PARTICLE, next.category());
    }

    @Test
    void isEquippedIgnoresPermissionsEntirely() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.ANIMATION, "purple_charge");

        // The menu marks the row the player CHOSE, whether or not it currently plays. Hiding the
        // mark when a rank lapses would make a renewal look like the choice was lost.
        assertTrue(EffectSelection.isEquipped(worn, ANIMATION));
        assertFalse(EffectSelection.isEquipped(worn, PARTICLE));
        assertFalse(EffectSelection.isEquipped(null, ANIMATION));
    }

    // --- Permission lost -> stored but inert ----------------------------------

    @Test
    void anEquippedEffectPlaysWhileThePermissionIsHeld() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.PARTICLE, "portal");

        EffectEntry resolved = EffectSelection.resolve(CATALOG_VALUE, worn, true,
                holding("polaroidhomes.particle.portal"));

        assertSame(PARTICLE, resolved);
    }

    @Test
    void losingThePermissionStopsTheEffectWithoutTouchingTheChoice() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.PARTICLE, "portal");

        assertNull(EffectSelection.resolve(CATALOG_VALUE, worn, true, HOLDS_NOTHING));
        // The choice itself is untouched by resolving: nothing in this path writes, so a lapsed
        // rank cannot clear a row. That is what makes the next assertion possible.
        assertEquals(new EquippedEffect(EffectCategory.PARTICLE, "portal"), worn);
    }

    @Test
    void gettingThePermissionBackMakesTheStoredChoicePlayAgainWithoutReEquipping() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.PARTICLE, "portal");

        assertNull(EffectSelection.resolve(CATALOG_VALUE, worn, true, HOLDS_NOTHING));
        // Same stored value, renewed rank, no re-equip in between.
        assertSame(PARTICLE, EffectSelection.resolve(CATALOG_VALUE, worn, true,
                holding("polaroidhomes.particle.portal")));
    }

    @Test
    void holdingTheOtherCategorysNodeIsNotEnough() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.PARTICLE, "portal");

        assertNull(EffectSelection.resolve(CATALOG_VALUE, worn, true,
                holding("polaroidhomes.animation.portal")));
    }

    // --- Nothing to play ------------------------------------------------------

    @Test
    void nothingEquippedResolvesToACleanTeleport() {
        assertNull(EffectSelection.resolve(CATALOG_VALUE, null, true, HOLDS_EVERYTHING));
    }

    @Test
    void anEntryRemovedFromTheCatalogResolvesToNothingRatherThanFailing() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.PARTICLE, "deleted_by_operator");

        assertNull(EffectSelection.resolve(CATALOG_VALUE, worn, true, HOLDS_EVERYTHING));
    }

    @Test
    void anAnimationResolvesToNothingWhenModelEngineIsAbsentEvenIfOwned() {
        EquippedEffect worn = new EquippedEffect(EffectCategory.ANIMATION, "purple_charge");

        assertNull(EffectSelection.resolve(CATALOG_VALUE, worn, false, HOLDS_EVERYTHING));
        assertSame(ANIMATION, EffectSelection.resolve(CATALOG_VALUE, worn, true, HOLDS_EVERYTHING));
    }

    // --- canEquip -------------------------------------------------------------

    @Test
    void canEquipNeedsBothThePermissionAndTheRenderer() {
        assertTrue(EffectSelection.canEquip(ANIMATION, true,
                holding("polaroidhomes.animation.purple_charge")));
        // Owned, but nothing can render it: equipping would replace a working effect with a dead
        // one, so it is refused rather than accepted and then silently inert.
        assertFalse(EffectSelection.canEquip(ANIMATION, false,
                holding("polaroidhomes.animation.purple_charge")));
        assertFalse(EffectSelection.canEquip(ANIMATION, true, HOLDS_NOTHING));
        // A particle needs no renderer to be installed, so the permission is the only question.
        assertTrue(EffectSelection.canEquip(PARTICLE, false,
                holding("polaroidhomes.particle.portal")));
    }

    // --- Stored values --------------------------------------------------------

    @Test
    void aStoredRowIsParsedBackIntoAChoice() {
        EquippedEffect parsed = EquippedEffect.of("particle", "Portal");

        assertNotNull(parsed);
        assertEquals(EffectCategory.PARTICLE, parsed.category());
        // Folded, so a hand-edited row in either spelling resolves against the catalog.
        assertEquals("portal", parsed.id());
    }

    @Test
    void aStoredRowNamingAnUnknownCategoryReadsAsNothingEquipped() {
        assertNull(EquippedEffect.of("sound", "portal"));
        assertNull(EquippedEffect.of(null, "portal"));
        assertNull(EquippedEffect.of("particle", null));
        assertNull(EquippedEffect.of("particle", "not a valid id"));
    }
}
