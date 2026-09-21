package me.juancayc.polaroidhomes.intercept;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the interception matching rule.
 *
 * <p>This is the half of the {@code /homes} answer that can go wrong quietly. Over-matching breaks
 * a working backend command - {@code /homes someplayer} is HuskHomes' own home list for another
 * player - and under-matching leaves the feature simply not happening, with nothing logged either
 * way. Both are pinned here.
 */
class CommandInterceptorTest {

    private static CommandInterceptor homesOnly() {
        Map<String, Boolean> configured = new LinkedHashMap<>();
        configured.put("homes", true);
        configured.put("home", false);
        return CommandInterceptor.of(configured);
    }

    private static CommandInterceptor both() {
        Map<String, Boolean> configured = new LinkedHashMap<>();
        configured.put("homes", true);
        configured.put("home", true);
        return CommandInterceptor.of(configured);
    }

    @Test
    void theShippedDefaultInterceptsHomesAndLeavesHomeAlone() {
        CommandInterceptor interceptor = homesOnly();

        assertTrue(interceptor.intercepts("/homes"));
        assertFalse(interceptor.intercepts("/home"));
    }

    @Test
    void bothSlashFormsMatch() {
        CommandInterceptor interceptor = homesOnly();

        assertTrue(interceptor.intercepts("/homes"));
        assertTrue(interceptor.intercepts("homes"));
    }

    @Test
    void matchingIgnoresCase() {
        CommandInterceptor interceptor = homesOnly();

        assertTrue(interceptor.intercepts("/HOMES"));
        assertTrue(interceptor.intercepts("/Homes"));
        assertTrue(interceptor.intercepts("/hOmEs"));
    }

    @Test
    void surroundingWhitespaceDoesNotStopAMatch() {
        CommandInterceptor interceptor = homesOnly();

        assertTrue(interceptor.intercepts("  /homes  "));
        assertTrue(interceptor.intercepts("/homes "));
    }

    /**
     * The rule that keeps the backend working. {@code /homes <player>} is HuskHomes'
     * {@code /homelist <player>}; swallowing it would break a command to add a menu nobody asked
     * for.
     */
    @Test
    void anArgumentIsNeverSwallowed() {
        CommandInterceptor interceptor = both();

        assertFalse(interceptor.intercepts("/homes someplayer"));
        assertFalse(interceptor.intercepts("/home base"));
        assertFalse(interceptor.intercepts("/homes a b c"));
    }

    @Test
    void aDoubleSpaceBeforeAnArgumentStillCountsAsAnArgument() {
        CommandInterceptor interceptor = homesOnly();

        assertFalse(interceptor.intercepts("/homes   someplayer"),
                "splitting on a single space would read the extra spaces as an empty argument and "
                        + "swallow a line that names a player");
    }

    /**
     * A namespaced form is a player naming the plugin they want. Answering it with a different
     * plugin's menu would be the one case where interception is dishonest rather than helpful.
     */
    @Test
    void aNamespacedFormIsNeverIntercepted() {
        CommandInterceptor interceptor = both();

        assertFalse(interceptor.intercepts("/essentials:homes"));
        assertFalse(interceptor.intercepts("/huskhomes:homes"));
        assertFalse(interceptor.intercepts("/minecraft:home"));
        assertFalse(interceptor.intercepts("essentials:homes"));
    }

    @Test
    void turningALabelOffStopsItBeingIntercepted() {
        Map<String, Boolean> configured = new LinkedHashMap<>();
        configured.put("homes", false);
        configured.put("home", false);
        CommandInterceptor interceptor = CommandInterceptor.of(configured);

        assertFalse(interceptor.isEnabled());
        assertFalse(interceptor.intercepts("/homes"));
        assertFalse(interceptor.intercepts("/home"));
    }

    @Test
    void anOperatorWhoTurnsHomeOnGetsTheBareFormOnly() {
        CommandInterceptor interceptor = both();

        assertTrue(interceptor.intercepts("/home"));
        assertFalse(interceptor.intercepts("/home base"),
                "the most-run command on most servers must keep teleporting when it carries a name");
    }

    @Test
    void anEmptyOrAbsentSectionInterceptsNothing() {
        assertFalse(CommandInterceptor.of(null).isEnabled());
        assertFalse(CommandInterceptor.of(Map.of()).isEnabled());
        assertFalse(CommandInterceptor.disabled().intercepts("/homes"));
    }

    @Test
    void anUnrelatedCommandIsLeftAlone() {
        CommandInterceptor interceptor = both();

        assertFalse(interceptor.intercepts("/homemenu"));
        assertFalse(interceptor.intercepts("/sethome"));
        assertFalse(interceptor.intercepts("/homelist"));
        assertFalse(interceptor.intercepts("/spawn"));
    }

    @Test
    void anEmptyOrNullLineIsNotIntercepted() {
        CommandInterceptor interceptor = both();

        assertFalse(interceptor.intercepts(null));
        assertFalse(interceptor.intercepts(""));
        assertFalse(interceptor.intercepts("   "));
        assertFalse(interceptor.intercepts("/"));
    }

    @Test
    void aConfiguredLabelIsNormalizedSoASlashOrCaseInTheConfigStillWorks() {
        Map<String, Boolean> configured = new LinkedHashMap<>();
        configured.put("/HOMES", true);
        CommandInterceptor interceptor = CommandInterceptor.of(configured);

        assertTrue(interceptor.intercepts("/homes"));
    }
}
