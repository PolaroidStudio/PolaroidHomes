package me.juancayc.polaroidhomes.world;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the blacklist predicate.
 *
 * <p>Every rule here is one an operator gets wrong at 2am editing a YAML file, and the failure mode
 * of each is a player who cannot use a home and an operator who is sure they blocked the world.
 * None of them needs Bukkit, so none of them has an excuse for being untested.
 */
class WorldBlacklistTest {

    @Test
    void anEmptyListBlocksNothing() {
        WorldBlacklist blacklist = WorldBlacklist.of(List.of());

        assertTrue(blacklist.isEmpty());
        assertFalse(blacklist.isBlocked("world"));
        assertFalse(blacklist.isBlocked("world_nether"));
    }

    @Test
    void aNullListBlocksNothing() {
        assertTrue(WorldBlacklist.of(null).isEmpty());
        assertFalse(WorldBlacklist.of(null).isBlocked("anything"));
    }

    @Test
    void theEmptyFactoryBlocksNothing() {
        assertTrue(WorldBlacklist.empty().isEmpty());
        assertFalse(WorldBlacklist.empty().isBlocked("world"));
    }

    @Test
    void matchingIgnoresCaseInBothDirections() {
        WorldBlacklist blacklist = WorldBlacklist.of(List.of("World_Nether"));

        assertTrue(blacklist.isBlocked("world_nether"));
        assertTrue(blacklist.isBlocked("WORLD_NETHER"));
        assertTrue(blacklist.isBlocked("World_Nether"));
    }

    @Test
    void surroundingWhitespaceInTheConfigIsIgnored() {
        WorldBlacklist blacklist = WorldBlacklist.of(List.of("  world_the_end  "));

        assertTrue(blacklist.isBlocked("world_the_end"));
    }

    /**
     * A world that does not exist is kept and simply never matches. An operator may blacklist a
     * world before creating it, and dropping the entry would silently un-block it the day it
     * appears.
     */
    @Test
    void anEntryForAMissingWorldIsKeptAndInert() {
        WorldBlacklist blacklist = WorldBlacklist.of(List.of("a_world_nobody_ever_made"));

        assertFalse(blacklist.isEmpty());
        assertTrue(blacklist.isBlocked("a_world_nobody_ever_made"));
        assertFalse(blacklist.isBlocked("world"));
    }

    /**
     * A home whose world is not loaded reports no name. That home is already drawn without
     * coordinates; refusing it here would block it for a reason the operator never configured.
     */
    @Test
    void anUnknownWorldNameIsNotBlocked() {
        WorldBlacklist blacklist = WorldBlacklist.of(List.of("world"));

        assertFalse(blacklist.isBlocked(null));
        assertFalse(blacklist.isBlocked(""));
        assertFalse(blacklist.isBlocked("   "));
    }

    @Test
    void blankAndNullEntriesAreDroppedSoIsEmptyStaysHonest() {
        WorldBlacklist blacklist = WorldBlacklist.of(Arrays.asList("", "   ", null));

        assertTrue(blacklist.isEmpty(),
                "a list of nothing but blanks must read as empty, or the feature reports itself on "
                        + "while blocking no world at all");
    }

    @Test
    void entriesAreStoredNormalized() {
        WorldBlacklist blacklist = WorldBlacklist.of(List.of("World", " NETHER "));

        assertEquals(java.util.Set.of("world", "nether"), blacklist.names());
    }

    @Test
    void aPartialNameDoesNotMatch() {
        WorldBlacklist blacklist = WorldBlacklist.of(List.of("world"));

        assertFalse(blacklist.isBlocked("world_nether"),
                "matching is whole-name; 'world' must not drag 'world_nether' in with it");
        assertFalse(blacklist.isBlocked("myworld"));
    }
}
