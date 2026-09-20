package me.juancayc.polaroidhomes.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

/**
 * Parses the click-action field of a localized message.
 *
 * <p>Supported tokens:
 * <pre>
 *   [NONE]                    no action
 *   [RUN_COMMAND] /cmd        runs the command as the player
 *   [EXECUTE] /cmd            alias of RUN_COMMAND
 *   [SUGGEST_COMMAND] /cmd    suggests the command in chat
 *   [SUGGEST] /cmd            alias of SUGGEST_COMMAND
 *   [OPEN_URL] https://...    opens a URL
 *   [URL] https://...         alias of OPEN_URL
 *   [COPY] text               copies text to the clipboard
 * </pre>
 *
 * <p>The grammar is closed on purpose. An invented token produces a message with no click action
 * instead of an error, and a silently inert hyperlink in a translated file is the hardest kind of
 * defect to notice.
 */
public final class ClickActionParser {

    private ClickActionParser() {
    }

    public static Component apply(Component component, String action, String playerName) {
        if (action == null || action.isBlank() || action.startsWith("[NONE]")) {
            return component;
        }

        if (action.startsWith("[RUN_COMMAND] ") || action.startsWith("[EXECUTE] ")) {
            String prefix = action.startsWith("[RUN_COMMAND] ") ? "[RUN_COMMAND] " : "[EXECUTE] ";
            String command = action.substring(prefix.length()).replace("%player_name%", playerName);
            return component.clickEvent(ClickEvent.runCommand(command));
        }
        if (action.startsWith("[SUGGEST_COMMAND] ") || action.startsWith("[SUGGEST] ")) {
            String prefix = action.startsWith("[SUGGEST_COMMAND] ") ? "[SUGGEST_COMMAND] " : "[SUGGEST] ";
            String command = action.substring(prefix.length()).replace("%player_name%", playerName);
            return component.clickEvent(ClickEvent.suggestCommand(command));
        }
        if (action.startsWith("[OPEN_URL] ") || action.startsWith("[URL] ")) {
            String prefix = action.startsWith("[OPEN_URL] ") ? "[OPEN_URL] " : "[URL] ";
            return component.clickEvent(ClickEvent.openUrl(action.substring(prefix.length())));
        }
        if (action.startsWith("[COPY] ")) {
            String text = action.substring("[COPY] ".length()).replace("%player_name%", playerName);
            return component.clickEvent(ClickEvent.copyToClipboard(text));
        }
        return component;
    }
}
