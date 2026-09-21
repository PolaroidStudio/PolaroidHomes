package me.juancayc.polaroidhomes.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 *
 * <h2>Two kinds of label, and why the second looks like a violation of the first</h2>
 *
 * <p><b>Read this before "fixing" anything here.</b> This plugin now deals with labels in two
 * completely different ways, and the rule for one is the exact opposite of the rule for the other.
 *
 * <ul>
 *   <li>{@link #REGISTERED_LABELS} are labels this plugin <em>registers</em> through the COMMANDS
 *       lifecycle event. Every one of them <b>must be free</b>. A collision here is the original
 *       bug: the registration loses the race and the command silently never arrives.</li>
 *   <li>{@link #INTERCEPTED_LABELS} are labels this plugin <em>never registers</em> and instead
 *       catches in {@code PlayerCommandPreprocessEvent}. Every one of them <b>is deliberately a
 *       label another plugin owns</b> — that is the entire reason interception exists. Asserting
 *       they are free would be asserting the feature is pointless, and "correcting" it by moving
 *       {@code homes} into the registered list would reintroduce the exact bug this file was
 *       written to prevent.</li>
 * </ul>
 *
 * <p>The two sets must therefore stay disjoint, which is its own test below.
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

    /**
     * Every label this plugin REGISTERS: the command name first, then its aliases. Each one must be
     * free of both backends.
     *
     * <p>Mirrors {@code PolaroidHomesPlugin#registerCommand()}.
     */
    private static final List<String> REGISTERED_LABELS =
            List.of("homemenu", "phomes", "homesmenu", "hmenu");

    /**
     * Every label this plugin INTERCEPTS rather than registers. Each one is expected to belong to a
     * backend — see the class comment.
     *
     * <p>Mirrors the {@code commands.intercept} section of config.yml, which is the only place these
     * are read from. {@code home} is shipped off by default; it is listed here because the operator
     * may turn it on, and the interception rule has to hold for it either way.
     */
    private static final List<String> INTERCEPTED_LABELS = List.of("homes", "home");

    @Test
    void noRegisteredLabelCollidesWithEssentials() {
        assertNoCollision(REGISTERED_LABELS, ESSENTIALS_HOME_LABELS, "EssentialsX");
    }

    @Test
    void noRegisteredLabelCollidesWithHuskHomes() {
        assertNoCollision(REGISTERED_LABELS, HUSKHOMES_HOME_LABELS, "HuskHomes");
    }

    /**
     * The inverse assertion, and the reason it is here: an intercepted label that nobody else owns
     * should have been registered instead. Registration puts the command in the client's tab list,
     * gives it a permission gate and a description, and cannot be bypassed by a chat filter that
     * cancels the preprocess event — interception has none of that. Paying those costs for a label
     * that was free the whole time is a mistake this catches.
     */
    @Test
    void everyInterceptedLabelIsOwnedByABackend() {
        for (String label : INTERCEPTED_LABELS) {
            assertTrue(ESSENTIALS_HOME_LABELS.contains(label)
                            || HUSKHOMES_HOME_LABELS.contains(label),
                    "'" + label + "' is intercepted but no backend claims it. A free label should "
                            + "be registered rather than intercepted: registration gives it tab "
                            + "completion, a permission gate and a description, none of which "
                            + "interception has.");
        }
    }

    /**
     * The two mechanisms must never be applied to one label. Registering a label and also
     * intercepting it means the interception fires first and the registration becomes dead code
     * that is nonetheless still racing a backend.
     */
    @Test
    void noLabelIsBothRegisteredAndIntercepted() {
        for (String label : INTERCEPTED_LABELS) {
            assertFalse(REGISTERED_LABELS.contains(label),
                    "'" + label + "' is both registered and intercepted; pick one.");
        }
    }

    private static void assertNoCollision(List<String> ours, Set<String> taken, String plugin) {
        for (String label : ours) {
            assertFalse(taken.contains(label),
                    "'" + label + "' is taken by " + plugin + "; it would silently never reach this "
                            + "plugin because the home provider loads first.");
        }
    }
}
