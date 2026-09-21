package me.juancayc.polaroidhomes.edit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The click-twice-to-delete state machine.
 *
 * <p>Deleting a home is the one action in this menu that cannot be undone, so a single click must
 * never do it. The second window a confirmation dialog would need is not used here on purpose: the
 * home button is already the thing the player is looking at, so arming it in place and saying so in
 * its own lore keeps the decision and its target in one spot, and a mis-aimed second click lands on
 * a different home that is not armed and therefore does nothing.
 *
 * <p>Bukkit-free and clock-injected, so the window expiring is a unit test rather than a stopwatch
 * and a live server. A clock this class reads itself would make "the arming expires" untestable,
 * which is exactly the property that must not rot.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li>One armed home per player. Arming a second disarms the first, so a player can never have
 *       two homes one click from deletion.</li>
 *   <li>Arming expires after {@link #WINDOW_MILLIS}. A player who armed a delete, got distracted
 *       and came back to click again gets the arming prompt, not a deletion.</li>
 *   <li>Confirming consumes the arming, so a doubled click packet cannot delete a second home: the
 *       state is gone before the second dispatch resolves.</li>
 * </ul>
 */
public final class DeleteConfirmations {

    /**
     * How long an armed delete stays armed.
     *
     * <p>Long enough to read the lore line that says what the next click does, short enough that it
     * cannot still be armed after the player has moved on to another task.
     */
    public static final long WINDOW_MILLIS = 5_000L;

    /** What a click on a home's delete action resolved to. */
    public enum Outcome {
        /** Nothing was armed, or the arming had expired. The home is now armed. */
        ARMED,
        /** This home was armed and still within the window. The caller deletes it. */
        CONFIRMED
    }

    private record Armed(String home, long expiresAt) {
    }

    private final Map<UUID, Armed> armed = new HashMap<>();
    private final long windowMillis;

    public DeleteConfirmations() {
        this(WINDOW_MILLIS);
    }

    public DeleteConfirmations(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    /**
     * Resolves a delete click.
     *
     * @param player the clicking player
     * @param home   the home the clicked button belongs to
     * @param now    the current time in millis, passed in so the window is testable
     */
    public Outcome click(UUID player, String home, long now) {
        Armed current = armed.get(player);
        if (current != null && now < current.expiresAt() && matches(current.home(), home)) {
            // Consumed before the caller acts: a duplicated click packet that slipped past the
            // listener's cooldown finds nothing armed and re-arms instead of deleting twice.
            armed.remove(player);
            return Outcome.CONFIRMED;
        }
        armed.put(player, new Armed(home, now + windowMillis));
        return Outcome.ARMED;
    }

    /**
     * True when this exact home is armed for this player right now.
     *
     * <p>Read by the renderer so the armed button can say so in its own lore. Expiry is checked
     * here too rather than swept on a timer: a stale entry costs nothing until it is read, and a
     * timer that sweeps a map this small would be more moving parts than the problem.
     */
    public boolean isArmed(UUID player, String home, long now) {
        Armed current = armed.get(player);
        return current != null && now < current.expiresAt() && matches(current.home(), home);
    }

    /** Drops a player's arming. Called on close and on quit, so a window never outlives its state. */
    public void clear(UUID player) {
        armed.remove(player);
    }

    /** Drops every arming. Called on reload, when every window is closed anyway. */
    public void clearAll() {
        armed.clear();
    }

    /**
     * Home names are compared case-insensitively, because EssentialsX folds a home name to
     * lowercase and the name on the button may therefore differ in case from the one the backend
     * answers with.
     */
    private static boolean matches(String a, String b) {
        return a != null && b != null
                && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }
}
