package me.juancayc.polaroidhomes.edit;

import me.juancayc.polaroidhomes.edit.DeleteConfirmations.Outcome;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the delete confirmation state machine.
 *
 * <p>The property under test is that a single click never deletes a home. Every case below is a way
 * that could stop being true: a doubled click packet, a stale arming, a mis-aimed second click, a
 * second player's arming leaking into the first's. None of them needs a server, and the failure
 * mode of all of them is a home the player cannot get back.
 *
 * <p>The clock is passed in rather than read, which is what makes the expiry a test rather than a
 * stopwatch.
 */
class DeleteConfirmationsTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final long T0 = 1_000_000L;

    @Test
    void theFirstClickArmsRatherThanDeletes() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        assertEquals(Outcome.ARMED, confirmations.click(PLAYER, "base", T0));
    }

    @Test
    void theSecondClickInsideTheWindowConfirms() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);

        assertEquals(Outcome.CONFIRMED, confirmations.click(PLAYER, "base", T0 + 1000L));
    }

    /**
     * Confirming consumes the arming, so a duplicated click packet that slipped past the listener's
     * cooldown finds nothing armed and re-arms instead of deleting a second time.
     */
    @Test
    void confirmingConsumesTheArmingSoAThirdClickRearms() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);
        assertEquals(Outcome.CONFIRMED, confirmations.click(PLAYER, "base", T0 + 100L));
        assertEquals(Outcome.ARMED, confirmations.click(PLAYER, "base", T0 + 200L));
    }

    @Test
    void anArmingExpiresAndTheNextClickArmsAgain() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);
        long afterWindow = T0 + DeleteConfirmations.WINDOW_MILLIS + 1L;

        assertEquals(Outcome.ARMED, confirmations.click(PLAYER, "base", afterWindow),
                "a player who armed a delete, got distracted and came back must be warned again, "
                        + "not have the home deleted by the click they expected to be the warning");
    }

    @Test
    void theArmingIsStillLiveOneMillisecondBeforeItExpires() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);
        long justInside = T0 + DeleteConfirmations.WINDOW_MILLIS - 1L;

        assertEquals(Outcome.CONFIRMED, confirmations.click(PLAYER, "base", justInside));
    }

    @Test
    void theArmingIsDeadExactlyAtTheBoundary() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);

        assertEquals(Outcome.ARMED,
                confirmations.click(PLAYER, "base", T0 + DeleteConfirmations.WINDOW_MILLIS));
    }

    /**
     * The safety property of arming in place: a second click that lands on a different home is on a
     * button that was never armed, so it warns rather than deletes.
     */
    @Test
    void aSecondClickOnADifferentHomeArmsThatOneInstead() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);

        assertEquals(Outcome.ARMED, confirmations.click(PLAYER, "shop", T0 + 100L));
        assertFalse(confirmations.isArmed(PLAYER, "base", T0 + 100L),
                "arming a second home must disarm the first, or the player has two homes one "
                        + "click from deletion");
        assertTrue(confirmations.isArmed(PLAYER, "shop", T0 + 100L));
    }

    @Test
    void onePlayersArmingDoesNotReachAnother() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);

        assertEquals(Outcome.ARMED, confirmations.click(OTHER, "base", T0 + 100L));
        assertTrue(confirmations.isArmed(PLAYER, "base", T0 + 100L));
    }

    /**
     * EssentialsX folds a home name to lowercase, so the name on the button may differ in case from
     * the one the backend answers with. A case difference must not read as a different home.
     */
    @Test
    void homeNamesAreComparedCaseInsensitively() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "Base", T0);

        assertEquals(Outcome.CONFIRMED, confirmations.click(PLAYER, "base", T0 + 100L));
    }

    @Test
    void isArmedReportsTheLiveStateAndRespectsExpiry() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        assertFalse(confirmations.isArmed(PLAYER, "base", T0));
        confirmations.click(PLAYER, "base", T0);
        assertTrue(confirmations.isArmed(PLAYER, "base", T0 + 100L));
        assertFalse(confirmations.isArmed(PLAYER, "base",
                T0 + DeleteConfirmations.WINDOW_MILLIS + 1L));
        assertFalse(confirmations.isArmed(PLAYER, "shop", T0 + 100L));
    }

    @Test
    void clearDropsOnePlayersArmingAndLeavesAnotherAlone() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);
        confirmations.click(OTHER, "shop", T0);

        confirmations.clear(PLAYER);

        assertFalse(confirmations.isArmed(PLAYER, "base", T0 + 100L));
        assertTrue(confirmations.isArmed(OTHER, "shop", T0 + 100L));
    }

    @Test
    void clearAllDropsEverything() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);
        confirmations.click(OTHER, "shop", T0);

        confirmations.clearAll();

        assertFalse(confirmations.isArmed(PLAYER, "base", T0 + 100L));
        assertFalse(confirmations.isArmed(OTHER, "shop", T0 + 100L));
    }

    /**
     * Clearing is what the close and quit hooks do, and the point of it is that reopening the menu
     * within the window does not find a live arming.
     */
    @Test
    void aClearedArmingCannotBeConfirmed() {
        DeleteConfirmations confirmations = new DeleteConfirmations();

        confirmations.click(PLAYER, "base", T0);
        confirmations.clear(PLAYER);

        assertEquals(Outcome.ARMED, confirmations.click(PLAYER, "base", T0 + 100L));
    }

    @Test
    void anInjectedWindowIsHonoured() {
        DeleteConfirmations confirmations = new DeleteConfirmations(100L);

        confirmations.click(PLAYER, "base", T0);

        assertEquals(Outcome.CONFIRMED, confirmations.click(PLAYER, "base", T0 + 50L));
        confirmations.click(PLAYER, "base", T0);
        assertEquals(Outcome.ARMED, confirmations.click(PLAYER, "base", T0 + 150L));
    }
}
