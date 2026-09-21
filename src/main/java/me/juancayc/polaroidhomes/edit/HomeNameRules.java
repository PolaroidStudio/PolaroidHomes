package me.juancayc.polaroidhomes.edit;

import java.util.Collection;
import java.util.Locale;

/**
 * Validates a home name the player typed, before it is handed to a backend.
 *
 * <p>Bukkit-free so every rule runs in a unit test. That matters more here than elsewhere: a name
 * this class lets through is written into somebody else's database, and the failure mode of a bad
 * one is not an exception but a home that exists under a name no command can address.
 *
 * <h2>Why these rules and not the backend's</h2>
 * <p>Neither backend exposes its own validator. EssentialsX lowercases a home name and stores it as
 * a YAML key; HuskHomes stores it as a row and uses {@code Home.getDelimiter()} to build
 * {@code owner.home} identifiers. So the rules below are the intersection of what both can carry
 * back out intact, applied before either is asked: a name rejected here never reaches a backend, and
 * a name accepted here addresses the same home afterwards as before.
 */
public final class HomeNameRules {

    /**
     * Longest accepted name.
     *
     * <p>Not a backend limit — neither declares one — but a menu one: the name is the button's
     * display name, and a name longer than this is unreadable in a tooltip and unusable in the chat
     * line that confirms it.
     */
    public static final int MAX_LENGTH = 32;

    /** Why a name was refused. Each value has its own message key, so the player is told which. */
    public enum Result {
        /** Accepted. */
        OK("rename.ok"),
        /** Nothing but whitespace. */
        EMPTY("rename.invalid_empty"),
        /** Longer than {@link #MAX_LENGTH}. */
        TOO_LONG("rename.invalid_too_long"),
        /** Holds a character a backend would drop, fold or treat as a separator. */
        ILLEGAL_CHARACTERS("rename.invalid_characters"),
        /** The player already has a home under this name. */
        DUPLICATE("rename.invalid_duplicate"),
        /** The new name is the old one, so nothing would change. */
        UNCHANGED("rename.invalid_unchanged");

        private final String messageKey;

        Result(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }

        public boolean isOk() {
            return this == OK;
        }
    }

    private HomeNameRules() {
    }

    /**
     * Checks a proposed name against the player's existing homes.
     *
     * @param proposed the raw text the player typed, not yet trimmed
     * @param current  the home being renamed, so renaming to its own name is reported as unchanged
     *                 rather than as a duplicate against itself
     * @param existing every home name the player currently has
     */
    public static Result check(String proposed, String current, Collection<String> existing) {
        if (proposed == null) {
            return Result.EMPTY;
        }
        String name = proposed.strip();
        if (name.isEmpty()) {
            return Result.EMPTY;
        }
        if (name.length() > MAX_LENGTH) {
            return Result.TOO_LONG;
        }
        if (!isAddressable(name)) {
            return Result.ILLEGAL_CHARACTERS;
        }
        // Compared case-insensitively because EssentialsX folds a home name to lowercase before
        // storing it: "Base" and "base" are one home there, so accepting the second as a rename of
        // the first would silently destroy one of them.
        if (current != null && name.equalsIgnoreCase(current.strip())) {
            return Result.UNCHANGED;
        }
        if (existing != null) {
            for (String taken : existing) {
                if (taken != null && taken.strip().equalsIgnoreCase(name)) {
                    return Result.DUPLICATE;
                }
            }
        }
        return Result.OK;
    }

    /**
     * The name as it will be stored: stripped, nothing else.
     *
     * <p>Case is deliberately left alone. EssentialsX folds it and HuskHomes does not, so folding
     * here would make this plugin's stored icon key disagree with HuskHomes' own name for the same
     * home. Each provider applies its own convention; this returns what the player typed.
     */
    public static String normalize(String proposed) {
        return proposed == null ? "" : proposed.strip();
    }

    /**
     * True when every character survives a round trip through both backends.
     *
     * <p>Letters, digits, underscore and hyphen only. Everything else is refused for a concrete
     * reason rather than out of caution:
     * <ul>
     *   <li>whitespace — a home name is an argument to {@code /home <name>}, so a space splits it
     *       into two arguments and the home becomes unreachable by command;</li>
     *   <li>{@code .} — HuskHomes' default {@code Home.getDelimiter()}, which separates owner from
     *       home in the identifiers its own commands parse;</li>
     *   <li>{@code :} — reads as a command namespace in the chat line the player would type;</li>
     *   <li>{@code <} and {@code >} and the rest of MiniMessage's punctuation — the name is
     *       rendered into lore and chat, and while every substitution here is escaped, a name the
     *       player cannot type back is a name they cannot use.</li>
     * </ul>
     * Ascii-only, for the same reason: a name in a script the server's console cannot print is a
     * name an operator cannot act on.
     */
    private static boolean isAddressable(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /** Lowercased form, for a case-insensitive comparison a caller has to make itself. */
    public static String fold(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}
