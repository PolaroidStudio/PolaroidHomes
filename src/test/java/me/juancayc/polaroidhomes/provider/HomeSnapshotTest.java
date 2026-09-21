package me.juancayc.polaroidhomes.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the menu behaves on a provider that has no tiers.
 *
 * <p>Tiers are an EssentialsX concept: it enumerates named groups from its own
 * {@code sethome-multiple} section. HuskHomes resolves a limit from numeric
 * {@code huskhomes.max_homes.<n>} permissions and keeps no list of the ranks granting them, so it
 * reports no tiers at all. These tests pin down what the menu then does, because the wrong answer is
 * not a crash — it is a padlock whose lore reads "unlocks at None", which looks like a broken config
 * rather than a backend that simply does not have the information.
 */
class HomeSnapshotTest {

    private static HomeSnapshot withoutTiers(int limit, String... homes) {
        return new HomeSnapshot(List.of(homes), limit, List.of(), Map.of());
    }

    @Test
    @DisplayName("with no tiers the grid is sized to the player's own limit")
    void noTiersSizesTheGridToTheOwnLimit() {
        // Nothing above the player's allowance can be shown, because there is nothing to say about it.
        // Sizing to their own limit is what makes the grid hold no locked slots at all.
        assertEquals(5, withoutTiers(5, "base", "mine").highestLimit());
    }

    @Test
    @DisplayName("with tiers the grid is sized to the highest one, above the player's own limit")
    void tiersStillDriveTheGrid() {
        // The contrast that matters: with tiers the grid deliberately overshoots, so a player can see
        // what the ranks above them are worth.
        HomeSnapshot snapshot = new HomeSnapshot(List.of("base"), 3,
                HomeTier.sorted(Map.of("default", 3, "vip", 10)), Map.of());

        assertEquals(10, snapshot.highestLimit());
    }

    @Test
    @DisplayName("a tier-less snapshot attributes no rank to any slot, at any index")
    void noTiersAttributesNothing() {
        HomeSnapshot snapshot = withoutTiers(3, "base");

        // The menu asks this per locked slot, and a null is what routes it to the unattributed lore
        // instead of substituting a rank name it does not have.
        for (int index = 0; index < 8; index++) {
            assertNull(HomeTier.unlockingSlot(snapshot.tiers(), index),
                    "slot index " + index + " must name no rank");
        }
    }

    @Test
    @DisplayName("an empty snapshot is a usable grid, not a null hazard")
    void emptyIsUsable() {
        HomeSnapshot empty = HomeSnapshot.empty();

        // What an offline target or an unreachable backend produces. Every accessor still answers, so
        // the menu draws an empty grid rather than throwing halfway through a render.
        assertTrue(empty.homes().isEmpty());
        assertEquals(0, empty.limit());
        assertTrue(empty.tiers().isEmpty());
        assertEquals(0, empty.highestLimit());
        assertNull(empty.location("anything"));
    }

    @Test
    @DisplayName("a home with no resolvable location still appears, without coordinates")
    void aHomeWithoutALocationStillAppears() {
        // A home in an unloaded world, or on another server in a proxied HuskHomes network. The home is
        // real and teleporting to it works, so dropping it from the list would be a worse lie than
        // drawing it without coordinates.
        HomeSnapshot snapshot = withoutTiers(3, "base", "faraway");

        assertEquals(List.of("base", "faraway"), snapshot.homes());
        assertNull(snapshot.location("faraway"));
    }

    @Test
    @DisplayName("the snapshot is immutable, so a render cannot be changed under it")
    void snapshotIsImmutable() {
        HomeSnapshot snapshot = withoutTiers(3, "base");

        assertNotNull(snapshot.homes());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.homes().add("injected"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.tiers().clear());
    }
}
