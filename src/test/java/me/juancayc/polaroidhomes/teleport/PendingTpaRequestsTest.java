package me.juancayc.polaroidhomes.teleport;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the tpa-versus-tpahere rule and the mark's lifetime.
 *
 * <p>These are the two ways this can go wrong quietly. Getting the direction backwards decorates
 * exactly the player the user said must not be decorated, and a mark that never expires leaves an
 * unaccepted request decorating whatever that player does next, hours later. Neither would throw,
 * and neither would show up in a log.
 */
class PendingTpaRequestsTest {

    private static final long T0 = 1_000_000L;

    private final UUID requester = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private PendingTpaRequests requests() {
        return new PendingTpaRequests(PendingTpaRequests.WINDOW_MILLIS);
    }

    // --- The direction rule ---------------------------------------------------

    @Test
    void aTpaIsRemembered() {
        PendingTpaRequests requests = requests();

        assertTrue(requests.remember(requester, false, T0));
        assertTrue(requests.claim(requester, T0));
    }

    /** The whole point of the feature: /tpahere moves the recipient, and gets nothing. */
    @Test
    void aTpahereIsNotRemembered() {
        PendingTpaRequests requests = requests();

        assertFalse(requests.remember(requester, true, T0));
        assertFalse(requests.claim(requester, T0));
    }

    /**
     * A tpahere must not be recorded against the player it would actually move either. That is the
     * mistake a "key it on the traveller" reading invites, and it would decorate the exact case the
     * user excluded.
     */
    @Test
    void aTpahereLeavesNeitherPlayerMarked() {
        PendingTpaRequests requests = requests();

        requests.remember(requester, true, T0);

        assertFalse(requests.isPending(requester, T0));
        assertFalse(requests.isPending(other, T0));
        assertEquals(0, requests.size(T0));
    }

    @Test
    void onlyTheRequesterIsMarkedByATpa() {
        PendingTpaRequests requests = requests();

        requests.remember(requester, false, T0);

        assertTrue(requests.isPending(requester, T0));
        assertFalse(requests.isPending(other, T0));
    }

    // --- Claiming -------------------------------------------------------------

    /** One accepted request decorates one teleport, so the player's next /warp stays plain. */
    @Test
    void claimingConsumesTheMark() {
        PendingTpaRequests requests = requests();
        requests.remember(requester, false, T0);

        assertTrue(requests.claim(requester, T0));
        assertFalse(requests.claim(requester, T0));
    }

    @Test
    void aPlayerWithNoRequestClaimsNothing() {
        assertFalse(requests().claim(requester, T0));
    }

    /** Both backends keep one outstanding request per sender; a second replaces the first. */
    @Test
    void aSecondRequestReplacesTheFirst() {
        PendingTpaRequests requests = requests();

        requests.remember(requester, false, T0);
        requests.remember(requester, false, T0 + 60_000L);

        assertEquals(1, requests.size(T0 + 60_000L));
        assertTrue(requests.claim(requester, T0 + 60_000L));
        assertFalse(requests.claim(requester, T0 + 60_000L));
    }

    /**
     * A tpahere sent after a tpa does not silently keep the tpa's mark alive under the same key.
     * The tpa's own expiry still governs it, which is what this pins.
     */
    @Test
    void aLaterTpahereDoesNotExtendAnEarlierTpa() {
        PendingTpaRequests requests = requests();
        requests.remember(requester, false, T0);

        requests.remember(requester, true, T0 + PendingTpaRequests.WINDOW_MILLIS - 1L);

        assertFalse(requests.claim(requester, T0 + PendingTpaRequests.WINDOW_MILLIS + 1L));
    }

    // --- Expiry ---------------------------------------------------------------

    /** The case that matters: a request nobody ever accepts must not decorate anything later. */
    @Test
    void aRequestThatIsNeverAcceptedExpires() {
        PendingTpaRequests requests = requests();
        requests.remember(requester, false, T0);

        long afterWindow = T0 + PendingTpaRequests.WINDOW_MILLIS + 1L;

        assertFalse(requests.isPending(requester, afterWindow));
        assertFalse(requests.claim(requester, afterWindow));
        assertEquals(0, requests.size(afterWindow));
    }

    @Test
    void aMarkIsStillClaimableInsideTheWindow() {
        PendingTpaRequests requests = requests();
        requests.remember(requester, false, T0);

        assertTrue(requests.claim(requester, T0 + PendingTpaRequests.WINDOW_MILLIS - 1L));
    }

    /** The boundary is exclusive: a mark expiring exactly now is expired, not claimable. */
    @Test
    void theWindowBoundaryIsNotClaimable() {
        PendingTpaRequests requests = requests();
        requests.remember(requester, false, T0);

        assertFalse(requests.claim(requester, T0 + PendingTpaRequests.WINDOW_MILLIS));
    }

    /** Checking an expired mark drops it, so an idle player is not a permanent map entry. */
    @Test
    void anExpiredMarkIsSweptWhenItIsLookedAt() {
        PendingTpaRequests requests = new PendingTpaRequests(1_000L);
        requests.remember(requester, false, T0);

        assertFalse(requests.isPending(requester, T0 + 2_000L));
        assertEquals(0, requests.size(T0));
    }

    // --- Forgetting -----------------------------------------------------------

    /** A player who disconnects holding a request comes back with nothing armed. */
    @Test
    void forgettingDropsTheMark() {
        PendingTpaRequests requests = requests();
        requests.remember(requester, false, T0);

        requests.forget(requester);

        assertFalse(requests.claim(requester, T0));
    }

    @Test
    void forgettingOnePlayerLeavesTheOthersAlone() {
        PendingTpaRequests requests = requests();
        requests.remember(requester, false, T0);
        requests.remember(other, false, T0);

        requests.forget(requester);

        assertFalse(requests.claim(requester, T0));
        assertTrue(requests.claim(other, T0));
    }

    @Test
    void clearingDropsEveryMark() {
        PendingTpaRequests requests = requests();
        requests.remember(requester, false, T0);
        requests.remember(other, false, T0);

        requests.clear();

        assertEquals(0, requests.size(T0));
    }
}
