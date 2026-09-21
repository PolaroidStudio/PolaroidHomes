package me.juancayc.polaroidhomes.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the command labels against EssentialsX.
 *
 * <p>This plugin declares EssentialsX as a required BEFORE dependency, so EssentialsX always wins
 * a label both plugins claim — silently. The first release registered {@code homes}, which is an
 * alias of EssentialsX's own {@code /home}: players ran it, EssentialsX answered, and this menu
 * never opened. Nothing failed at build or enable time, which is exactly why the rule is pinned
 * here instead of left to review.
 *
 * <p>The list is taken from EssentialsX's shipped plugin.yml (2.21.0), home commands only.
 */
class CommandLabelTest {

    /** Command names and aliases EssentialsX registers for its home commands. */
    private static final Set<String> ESSENTIALS_HOME_LABELS = Set.of(
            "home", "ehome", "homes", "ehomes",
            "sethome", "esethome", "createhome", "ecreatehome",
            "delhome", "edelhome", "remhome", "eremhome", "rmhome", "ermhome",
            "renamehome", "erenamehome");

    /** Every label this plugin registers: the command name first, then its aliases. */
    private static final List<String> OUR_LABELS =
            List.of("homemenu", "phomes", "homesmenu", "hmenu");

    @Test
    void noLabelCollidesWithEssentials() {
        for (String label : OUR_LABELS) {
            assertFalse(ESSENTIALS_HOME_LABELS.contains(label),
                    "'" + label + "' is taken by EssentialsX; it would silently never reach this "
                            + "plugin because EssentialsX loads first.");
        }
    }
}
