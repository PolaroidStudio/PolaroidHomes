package me.juancayc.polaroidhomes.provider;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which backend gets chosen, and what happens when the answer is nothing.
 *
 * <p>This is the class the original bug lives in. The plugin used to assume EssentialsX, so on a
 * server managing homes with HuskHomes it read an empty list from a backend that held no homes and
 * rendered a perfectly correct empty grid. Every case below exists so that a wrong or absent backend
 * is a stated refusal rather than a menu that looks fine and shows nothing.
 *
 * <p>No Bukkit server is booted: {@link ProviderSelection} takes the candidate list as an argument
 * precisely so the decision can be tested without one.
 */
class ProviderSelectionTest {

    /** A provider that answers nothing, because selection never calls anything but isAvailable. */
    private record FakeProvider(String id, boolean available) implements HomeProvider {

        @Override
        public String pluginName() {
            return id;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean supportsTiers() {
            return false;
        }

        @Override
        public CompletableFuture<HomeSnapshot> snapshot(Player viewer, OfflinePlayer target) {
            return CompletableFuture.completedFuture(HomeSnapshot.empty());
        }

        @Override
        public boolean teleport(Player player, String home) {
            return false;
        }
    }

    private static final FakeProvider ESSENTIALS_ON = new FakeProvider("essentialsx", true);
    private static final FakeProvider ESSENTIALS_OFF = new FakeProvider("essentialsx", false);
    private static final FakeProvider HUSK_ON = new FakeProvider("huskhomes", true);
    private static final FakeProvider HUSK_OFF = new FakeProvider("huskhomes", false);

    // ------------------------------------------------------------------------------ auto

    @Test
    @DisplayName("auto with only EssentialsX installed picks EssentialsX")
    void autoPicksTheOnlyInstalledProvider() {
        ProviderSelection selection =
                ProviderSelection.resolve("auto", List.of(ESSENTIALS_ON, HUSK_OFF));

        assertSame(ESSENTIALS_ON, selection.chosen());
        assertEquals(ProviderSelection.Outcome.AUTO_SELECTED, selection.outcome());
    }

    @Test
    @DisplayName("auto with only HuskHomes installed picks HuskHomes, skipping the absent one")
    void autoSkipsAnUninstalledHigherPriorityProvider() {
        // The regression this whole change exists for: EssentialsX has the higher priority but is not
        // installed, so it must be passed over rather than selected and read as empty.
        ProviderSelection selection =
                ProviderSelection.resolve("auto", List.of(ESSENTIALS_OFF, HUSK_ON));

        assertSame(HUSK_ON, selection.chosen());
        assertEquals(ProviderSelection.Outcome.AUTO_SELECTED, selection.outcome());
    }

    @Test
    @DisplayName("auto with both installed takes the first by priority, deterministically")
    void autoIsDeterministicWhenBothAreInstalled() {
        // Two servers with the same plugins must choose the same backend, or a config that works on
        // one silently reads the wrong homes on the other. The list order is that priority.
        ProviderSelection first =
                ProviderSelection.resolve("auto", List.of(ESSENTIALS_ON, HUSK_ON));
        ProviderSelection again =
                ProviderSelection.resolve("auto", List.of(ESSENTIALS_ON, HUSK_ON));

        assertSame(ESSENTIALS_ON, first.chosen());
        assertSame(first.chosen(), again.chosen());
    }

    @Test
    @DisplayName("auto with nothing installed resolves to nothing rather than a provider that is off")
    void autoWithNothingInstalledIsUnresolved() {
        ProviderSelection selection =
                ProviderSelection.resolve("auto", List.of(ESSENTIALS_OFF, HUSK_OFF));

        assertFalse(selection.isResolved());
        assertNull(selection.chosen());
        assertEquals(ProviderSelection.Outcome.NONE_INSTALLED, selection.outcome());
    }

    @Test
    @DisplayName("a blank or missing value behaves as auto")
    void blankBehavesAsAuto() {
        for (String raw : new String[]{null, "", "   "}) {
            ProviderSelection selection = ProviderSelection.resolve(raw, List.of(HUSK_ON));

            assertSame(HUSK_ON, selection.chosen(), "for value " + raw);
            assertEquals(ProviderSelection.AUTO, selection.request());
        }
    }

    // -------------------------------------------------------------------------- explicit

    @Test
    @DisplayName("a named provider that is installed is used even when a higher-priority one also is")
    void explicitBeatsPriority() {
        ProviderSelection selection =
                ProviderSelection.resolve("huskhomes", List.of(ESSENTIALS_ON, HUSK_ON));

        assertSame(HUSK_ON, selection.chosen());
        assertEquals(ProviderSelection.Outcome.EXPLICIT_SELECTED, selection.outcome());
    }

    @Test
    @DisplayName("the name is matched case-insensitively and trimmed")
    void explicitNameIsNormalised() {
        ProviderSelection selection =
                ProviderSelection.resolve("  HuskHomes  ", List.of(ESSENTIALS_ON, HUSK_ON));

        assertSame(HUSK_ON, selection.chosen());
    }

    @Test
    @DisplayName("a named provider that is not installed fails instead of falling back")
    void explicitButMissingDoesNotFallBack() {
        // The important assertion is the null: EssentialsX IS installed here, and choosing it would be
        // the silent fallback that hides an operator's typo behind a working-looking menu.
        ProviderSelection selection =
                ProviderSelection.resolve("huskhomes", List.of(ESSENTIALS_ON, HUSK_OFF));

        assertFalse(selection.isResolved());
        assertNull(selection.chosen());
        assertEquals(ProviderSelection.Outcome.NAMED_NOT_INSTALLED, selection.outcome());
    }

    @Test
    @DisplayName("a name this build does not know is reported as unknown, not as not-installed")
    void unknownNameIsDistinguishedFromNotInstalled() {
        // The two failures need different fixes — install a plugin, versus correct a typo — so they
        // are different outcomes and produce different console lines.
        ProviderSelection selection =
                ProviderSelection.resolve("essentials", List.of(ESSENTIALS_ON, HUSK_ON));

        assertFalse(selection.isResolved());
        assertEquals(ProviderSelection.Outcome.UNKNOWN_NAME, selection.outcome());
    }

    // ---------------------------------------------------------------------------- logging

    @Test
    @DisplayName("every unresolved outcome names the key and the values that would work")
    void unresolvedOutcomesAreActionable() {
        List<HomeProvider> candidates = List.of(ESSENTIALS_OFF, HUSK_OFF);

        for (String request : new String[]{"auto", "huskhomes", "nonsense"}) {
            String line = ProviderSelection.resolve(request, candidates).describe(candidates);

            // An operator reading this is looking at a server that refused to enable a plugin, so the
            // line has to say what to install or what to write, not just that something went wrong.
            assertTrue(line.contains("essentialsx") && line.contains("huskhomes"),
                    "names the supported values, for " + request + ": " + line);
        }
    }

    @Test
    @DisplayName("a resolved outcome names the plugin chosen and how it was chosen")
    void resolvedOutcomeNamesTheProvider() {
        List<HomeProvider> candidates = List.of(ESSENTIALS_OFF, HUSK_ON);

        String auto = ProviderSelection.resolve("auto", candidates).describe(candidates);
        String explicit = ProviderSelection.resolve("huskhomes", candidates).describe(candidates);

        assertTrue(auto.contains("huskhomes"), auto);
        assertTrue(auto.contains("automatically"), auto);
        assertTrue(explicit.contains("hooks.home-provider"), explicit);
    }
}
