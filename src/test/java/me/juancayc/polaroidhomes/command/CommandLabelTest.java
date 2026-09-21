package me.juancayc.polaroidhomes.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the command labels against both home providers.
 *
 * <p>Whichever provider is installed declares {@code load: BEFORE}, so it registers its commands
 * first and wins any label both plugins claim — silently. The first release registered {@code homes},
 * which is an alias of EssentialsX's own {@code /home}: players ran it, EssentialsX answered, and this
 * menu never opened. Nothing failed at build or enable time, which is exactly why the rule is pinned
 * here instead of left to review.
 *
 * <p>Both lists are checked regardless of which provider is in use, because the label is registered
 * once at enable and cannot be varied by backend: a label safe on an EssentialsX server but taken on a
 * HuskHomes one would be the same silent failure, just on somebody else's server.
 */
class CommandLabelTest {

    /**
     * Command names and aliases EssentialsX registers for its home commands.
     *
     * <p>Taken from EssentialsX's shipped plugin.yml (2.21.0), home commands only.
     */
    private static final Set<String> ESSENTIALS_HOME_LABELS = Set.of(
            "home", "ehome", "homes", "ehomes",
            "sethome", "esethome", "createhome", "ecreatehome",
            "delhome", "edelhome", "remhome", "eremhome", "rmhome", "ermhome",
            "renamehome", "erenamehome");

    /**
     * Command names and aliases HuskHomes registers for its home commands.
     *
     * <p>HuskHomes has no {@code commands:} section in its plugin.yml — it registers in code — so this
     * list comes from its documented command set rather than from a file that can be read back. The
     * {@code huskhomes:} namespaced forms are omitted because a namespaced label cannot collide.
     */
    private static final Set<String> HUSKHOMES_HOME_LABELS = Set.of(
            "home", "homes", "homelist",
            "sethome", "delhome", "edithome",
            "phome", "publichome", "publichomelist", "phomelist");

    /** Every label this plugin registers: the command name first, then its aliases. */
    private static final List<String> OUR_LABELS =
            List.of("homemenu", "phomes", "homesmenu", "hmenu");

    @Test
    void noLabelCollidesWithEssentials() {
        assertNoCollision(ESSENTIALS_HOME_LABELS, "EssentialsX");
    }

    @Test
    void noLabelCollidesWithHuskHomes() {
        assertNoCollision(HUSKHOMES_HOME_LABELS, "HuskHomes");
    }

    private static void assertNoCollision(Set<String> taken, String plugin) {
        for (String label : OUR_LABELS) {
            assertFalse(taken.contains(label),
                    "'" + label + "' is taken by " + plugin + "; it would silently never reach this "
                            + "plugin because the home provider loads first.");
        }
    }
}
