package me.juancayc.polaroidhomes.intercept;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether a chat-typed command should be swallowed and turned into a menu open.
 *
 * <p>This exists because {@code /homes} cannot be registered. Both backends already claim it —
 * EssentialsX as an alias of {@code /home}, HuskHomes as an alias of {@code /homelist} — and both
 * declare {@code load: BEFORE}, so a registration here loses the race with no error anywhere. That
 * was a real shipped bug, which is why {@code CommandLabelTest} pins the registered labels. This
 * class is the other half of the answer: the label is left to its owner and the typed line is
 * intercepted before the owner ever sees it.
 *
 * <p>Bukkit-free so the matching rule runs in a unit test. The rule is where the subtlety is, not
 * the event plumbing.
 *
 * <h2>The matching rule</h2>
 * <ol>
 *   <li>The label is compared case-insensitively, with or without its leading slash, because
 *       {@code PlayerCommandPreprocessEvent} hands over the raw line the player typed.</li>
 *   <li>A {@code plugin:label} form is reduced to its label. A player typing
 *       {@code /essentials:homes} is asking for a specific plugin's command, so the namespace is
 *       recognised and then... see the next point.</li>
 *   <li><b>Only the bare command is intercepted.</b> {@code /homes} opens the menu;
 *       {@code /homes someplayer} does not. The argument form belongs to the backend —
 *       {@code /homes <player>} is HuskHomes' own {@code /homelist <player>}, and
 *       {@code /home <name>} is the single most-run command on most servers. Swallowing either
 *       would break a working command to add a menu the player did not ask for.</li>
 *   <li>An explicit namespace is <b>not</b> intercepted at all. {@code /essentials:homes} is a
 *       player deliberately naming the plugin they want, and answering it with a different
 *       plugin's menu is the one case where interception would be dishonest rather than helpful.
 *       It is recognised only so that it is recognised as an escape hatch rather than falling
 *       through by accident.</li>
 * </ol>
 */
public final class CommandInterceptor {

    private final Set<String> labels;

    private CommandInterceptor(Set<String> labels) {
        this.labels = labels;
    }

    /** Intercepts nothing. */
    public static CommandInterceptor disabled() {
        return new CommandInterceptor(Set.of());
    }

    /**
     * Builds an interceptor from the {@code commands.intercept} map: label to enabled.
     *
     * <p>A label mapped to false is dropped rather than stored, so {@link #isEnabled()} answers
     * whether anything is actually intercepted rather than whether the section exists.
     */
    public static CommandInterceptor of(Map<String, Boolean> configured) {
        if (configured == null || configured.isEmpty()) {
            return disabled();
        }
        Map<String, Boolean> ordered = new LinkedHashMap<>(configured);
        Set<String> enabled = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Boolean> entry : ordered.entrySet()) {
            String label = entry.getKey();
            if (label != null && !label.isBlank() && Boolean.TRUE.equals(entry.getValue())) {
                enabled.add(strip(label));
            }
        }
        return new CommandInterceptor(Set.copyOf(enabled));
    }

    public boolean isEnabled() {
        return !labels.isEmpty();
    }

    /** The labels this interceptor swallows, normalized. */
    public Set<String> labels() {
        return labels;
    }

    /**
     * True when this typed line should be cancelled and answered with the menu.
     *
     * @param message the raw line from {@code PlayerCommandPreprocessEvent#getMessage()}, slash
     *                included or not
     */
    public boolean intercepts(String message) {
        if (message == null || labels.isEmpty()) {
            return false;
        }
        String line = message.strip();
        if (line.isEmpty()) {
            return false;
        }
        if (line.charAt(0) == '/') {
            line = line.substring(1).strip();
        }
        // Any whitespace at all means the player passed an argument, which belongs to whichever
        // plugin owns the label. Split on the first run of whitespace rather than on a single space
        // so a double space does not read as an empty argument and slip through as "bare".
        String[] parts = line.split("\s+", 2);
        if (parts.length > 1 && !parts[1].isBlank()) {
            return false;
        }
        String label = parts[0];
        if (label.indexOf(':') >= 0) {
            // A namespaced form names the plugin the player wants. Never ours, so never swallowed.
            return false;
        }
        return labels.contains(label.toLowerCase(Locale.ROOT));
    }

    private static String strip(String label) {
        String value = label.strip();
        if (!value.isEmpty() && value.charAt(0) == '/') {
            value = value.substring(1).strip();
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
