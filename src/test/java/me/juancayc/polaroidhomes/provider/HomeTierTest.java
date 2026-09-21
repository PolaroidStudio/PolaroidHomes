package me.juancayc.polaroidhomes.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The locked slots in the menu name the rank that unlocks them. Getting that wrong tells a paying
 * player they already own a perk they do not, so the attribution is pinned down here rather than
 * discovered on a live server.
 */
class HomeTierTest {

    private static Map<String, Integer> tiers(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("tiers sort ascending by limit regardless of the order they were written in")
    void sortsAscendingByLimit() {
        // Deliberately written highest-first, which is how most operators actually write the file.
        List<HomeTier> sorted = HomeTier.sorted(tiers("vip", 10, "default", 3, "mvp", 6));

        assertEquals(List.of("default", "mvp", "vip"),
                sorted.stream().map(HomeTier::group).toList());
    }

    @Test
    @DisplayName("equal limits break by name, so the order is stable across restarts")
    void tiesBreakByName() {
        // Two groups granting the same limit must always produce the same answer. Leaning on YAML
        // iteration order here would make the menu name a different rank after an operator
        // reshuffles the file, which reads to players as a bug.
        List<HomeTier> first = HomeTier.sorted(tiers("zeta", 5, "alpha", 5));
        List<HomeTier> second = HomeTier.sorted(tiers("alpha", 5, "zeta", 5));

        assertEquals(List.of("alpha", "zeta"), first.stream().map(HomeTier::group).toList());
        assertEquals(first, second);
    }

    @Test
    @DisplayName("the highest limit drives the grid size")
    void highestLimitWins() {
        List<HomeTier> sorted = HomeTier.sorted(tiers("default", 3, "vip", 10, "mvp", 6));

        assertEquals(10, HomeTier.highestLimit(sorted, 3));
    }

    @Test
    @DisplayName("with nothing configured the caller's fallback is used")
    void fallsBackWhenNothingConfigured() {
        assertEquals(3, HomeTier.highestLimit(List.of(), 3));
    }

    @Test
    @DisplayName("a locked slot names the cheapest rank that reaches it")
    void namesTheCheapestUnlockingTier() {
        List<HomeTier> sorted = HomeTier.sorted(tiers("default", 3, "mvp", 6, "vip", 10));

        // Slot index 3 is the fourth slot, so it needs a limit of at least four: mvp, not vip.
        assertEquals("mvp", HomeTier.unlockingSlot(sorted, 3).group());
        // Index 5 is the sixth slot, exactly what mvp grants.
        assertEquals("mvp", HomeTier.unlockingSlot(sorted, 5).group());
        // Index 6 is the seventh, one past mvp.
        assertEquals("vip", HomeTier.unlockingSlot(sorted, 6).group());
    }

    @Test
    @DisplayName("a slot no configured tier reaches names nothing rather than the top rank")
    void unreachableSlotHasNoTier() {
        List<HomeTier> sorted = HomeTier.sorted(tiers("default", 3, "vip", 10));

        assertNull(HomeTier.unlockingSlot(sorted, 10));
    }

    @Test
    @DisplayName("the first slot is covered by the cheapest tier")
    void firstSlotIsCoveredByTheCheapestTier() {
        List<HomeTier> sorted = HomeTier.sorted(tiers("vip", 10, "default", 1));

        assertEquals("default", HomeTier.unlockingSlot(sorted, 0).group());
    }
}
