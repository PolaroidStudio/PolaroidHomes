package me.juancayc.polaroidhomes.teleport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers which players have an outstanding {@code /tpa} that would move <em>them</em>.
 *
 * <p>This exists because neither backend says "this teleport came from a tpa" at the moment the
 * teleport happens. What they announce is the <em>request</em>, minutes earlier, and then an
 * ordinary teleport. Correlating the two is the only way to decorate an accepted {@code /tpa}
 * without also decorating {@code /warp}, {@code /spawn} and {@code /back}, which share that same
 * anonymous teleport path.
 *
 * <p>Keyed on the traveller, never on the pair. The requirement is that only the player who
 * actually moves sees the effect, and on the teleport side the traveller's UUID is the one thing
 * both backends hand over. A {@code /tpahere} is therefore never recorded at all — see
 * {@link #remember} — rather than recorded and filtered later, so there is no state a later bug
 * could read the wrong way round.
 *
 * <p>Bukkit-free and clock-injected, like {@code DeleteConfirmations}: expiry is the property most
 * likely to rot silently, and a class that read the clock itself would make it untestable.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li>Only a request that would move the requester is remembered. {@code /tpahere} moves the
 *       recipient, and the user asked for those to stay undecorated.</li>
 *   <li>One outstanding mark per traveller. A second {@code /tpa} replaces the first, matching
 *       both backends, which also keep one request per sender.</li>
 *   <li>A mark expires after {@link #WINDOW_MILLIS}. A request that is never accepted must not
 *       leave the player's next {@code /warp} decorated an hour later.</li>
 *   <li>Claiming consumes the mark, so one accepted request decorates exactly one teleport.</li>
 * </ul>
 */
public final class PendingTpaRequests {

    /**
     * How long an outstanding request stays claimable.
     *
     * <p>Comfortably longer than either backend's own request timeout (EssentialsX defaults to
     * 120 seconds, HuskHomes to 60), because expiring earlier than the backend would drop the mark
     * for a request the backend still considers live and silently lose the effect. Expiring later
     * is harmless: the backend refuses the acceptance, so nothing ever claims the mark and it is
     * swept on the next touch.
     */
    public static final long WINDOW_MILLIS = 300_000L;

    private final Map<UUID, Long> travellers = new HashMap<>();
    private final long windowMillis;

    public PendingTpaRequests() {
        this(WINDOW_MILLIS);
    }

    public PendingTpaRequests(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    /**
     * Records a teleport request, if it is the kind that moves the requester.
     *
     * @param requester   the player who asked; the traveller for {@code /tpa}
     * @param teleportHere true for {@code /tpahere}, where the recipient travels instead
     * @return true when the request was remembered
     */
    public boolean remember(UUID requester, boolean teleportHere, long now) {
        if (teleportHere) {
            // Deliberately not recorded under the recipient either: the recipient is the traveller
            // for a /tpahere, and recording them here is exactly how /tpahere would end up
            // decorated.
            return false;
        }
        travellers.put(requester, now + windowMillis);
        return true;
    }

    /**
     * Consumes {@code traveller}'s outstanding mark, if it has one that has not expired.
     *
     * @return true when this teleport is an accepted {@code /tpa} and should be decorated
     */
    public boolean claim(UUID traveller, long now) {
        Long expiresAt = travellers.remove(traveller);
        return expiresAt != null && expiresAt > now;
    }

    /** True while {@code traveller} has a live mark, without consuming it. */
    public boolean isPending(UUID traveller, long now) {
        Long expiresAt = travellers.get(traveller);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= now) {
            travellers.remove(traveller);
            return false;
        }
        return true;
    }

    /** Drops a mark outright: a declined request, or a player who disconnected holding one. */
    public void forget(UUID traveller) {
        travellers.remove(traveller);
    }

    public void clear() {
        travellers.clear();
    }

    /** Live marks, expired ones excluded. Exists so the expiry rule is observable from a test. */
    public int size(long now) {
        travellers.values().removeIf(expiresAt -> expiresAt <= now);
        return travellers.size();
    }
}
