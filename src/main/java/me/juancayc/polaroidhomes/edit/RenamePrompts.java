package me.juancayc.polaroidhomes.edit;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pending "type the new name in chat" prompts, one per player.
 *
 * <p>Chat is the input method because it is the only one this plugin can have without taking a new
 * dependency. A sign editor needs packets — Paper's {@code Player#openSign} opens a sign the client
 * edits, but reading the result back needs a packet listener this project does not have — and an
 * anvil-input menu would be a storage surface, which is exactly the class of window the menu-security
 * rules say to avoid when a chrome-only one will do. A chat prompt is plain Bukkit, and the cost it
 * pays is a timeout and a cancel word, both of which are here.
 *
 * <p>The prompt is deliberately not a conversation API: Bukkit's {@code Conversation} framework
 * suppresses all other chat for its duration, which on a busy server reads as the player being
 * muted. This reads one line and then gets out of the way.
 */
public final class RenamePrompts {

    /** What the player types to abandon the rename. Matched case-insensitively. */
    public static final String CANCEL_WORD = "cancel";

    /**
     * How long a prompt stays open, in seconds.
     *
     * <p>Long enough to think of a name, short enough that a forgotten prompt does not quietly
     * swallow the player's next ordinary chat line.
     */
    public static final int TIMEOUT_SECONDS = 30;

    /**
     * One open prompt.
     *
     * @param home      the home being renamed, captured when the prompt opened
     * @param expiresAt when the prompt stops accepting input
     */
    public record Pending(String home, long expiresAt) {
    }

    private final Map<UUID, Pending> pending = new HashMap<>();

    /** Opens a prompt, replacing whatever the player had open. */
    public void open(Player player, String home, long now) {
        pending.put(player.getUniqueId(),
                new Pending(home, now + TIMEOUT_SECONDS * 1000L));
    }

    /**
     * Takes the player's prompt, if one is open and has not expired.
     *
     * <p>Consuming rather than peeking: the chat listener reads one line and the prompt is done
     * either way, so leaving it in the map would make the player's next sentence a second rename
     * attempt.
     *
     * @return the pending prompt, or null when there was none or it had expired
     */
    public @Nullable Pending take(UUID player, long now) {
        Pending found = pending.remove(player);
        return found != null && now < found.expiresAt() ? found : null;
    }

    /** True when this player has a live prompt. Read only to decide whether to swallow a line. */
    public boolean hasPending(UUID player, long now) {
        Pending found = pending.get(player);
        if (found == null) {
            return false;
        }
        if (now >= found.expiresAt()) {
            // Dropped on read rather than swept on a timer: an expired prompt costs nothing until
            // somebody asks about it, and a timer over a map this small is more moving parts than
            // the problem.
            pending.remove(player);
            return false;
        }
        return true;
    }

    public void clear(UUID player) {
        pending.remove(player);
    }

    public void clearAll() {
        pending.clear();
    }

    /** True when the typed line is the cancel word. */
    public static boolean isCancel(String typed) {
        return typed != null && typed.strip().equalsIgnoreCase(CANCEL_WORD);
    }
}
