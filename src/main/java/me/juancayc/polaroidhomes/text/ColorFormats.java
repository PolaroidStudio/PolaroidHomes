package me.juancayc.polaroidhomes.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

/**
 * Normalizes every supported Minecraft color/style format to MiniMessage, then renders it.
 * Supported: legacy ampersand and section codes and styles, ampersand hex, section-expanded
 * legacy hex, bare hex, MiniMessage hex and native MiniMessage tags (passed through unchanged).
 */
public final class ColorFormats {

    // Replaced at startup with the shared (optionally Nexo-aware) instance, so a glyph tag in a
    // config value resolves the same way everywhere in the plugin.
    private static MiniMessage mm = MiniMessage.miniMessage();

    public static void init(MiniMessage instance) {
        mm = instance;
    }

    public static MiniMessage mm() {
        return mm;
    }

    // Section-expanded legacy hex.
    private static final Pattern SECTION_HEX = Pattern.compile("§x(§[0-9a-fA-F]){6}");
    // Simple section code.
    private static final Pattern SECTION_CODE = Pattern.compile("§([0-9a-fk-orA-FK-OR])");
    // Ampersand hex.
    private static final Pattern AMP_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    // Ampersand code.
    private static final Pattern AMP_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    // Bare hex: the lookbehind excludes hex that is already delimited, which would double-convert.
    private static final Pattern BARE_HEX =
            Pattern.compile("(?<![&§<#:])#([0-9a-fA-F]{6})(?![>0-9a-fA-F])");
    // A whole-string color token, in any supported syntax.
    private static final Pattern VALID_COLOR = Pattern.compile(
            "^(<#[0-9a-fA-F]{6}>|#[0-9a-fA-F]{6}|&#[0-9a-fA-F]{6}|"
                    + "§x(§[0-9a-fA-F]){6}|&[0-9a-fA-FK-OR]|§[0-9a-fA-FK-OR]|"
                    + "<(black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|"
                    + "dark_gray|blue|green|aqua|red|light_purple|yellow|white)>)$",
            Pattern.CASE_INSENSITIVE);

    private ColorFormats() {
    }

    /** Any color format to its MiniMessage equivalent. Already-valid MiniMessage is unchanged. */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return toLegacyToMM(input);
    }

    /** Parses text carrying colors in any supported format into a Component. */
    public static Component parse(String text) {
        if (text == null || text.isBlank()) {
            return Component.empty();
        }
        return mm.deserialize(normalize(text));
    }

    /** Serializes a Component back to legacy section codes. */
    public static String toLegacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    /** True when the whole string is a valid color in any supported format. */
    public static boolean isValidColor(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return VALID_COLOR.matcher(input.trim()).matches();
    }

    /** Converts section codes, ampersand codes and bare hex to tags; leaves existing tags intact. */
    public static String toLegacyToMM(String text) {
        if (text == null) {
            return "";
        }
        // Fast-path: no legacy or hex marker means nothing to convert. Menu rendering walks every
        // lore line of every slot on every open, so the early exit earns its keep.
        if (text.indexOf('§') < 0 && text.indexOf('&') < 0 && text.indexOf('#') < 0) {
            return text;
        }

        // Order matters. Section-hex must run before section-code and ampersand-hex before
        // ampersand-code, or the code pattern chews the first pair of an expanded hex run into a
        // colour tag and the rest leaks through as literal text.
        text = SECTION_HEX.matcher(text).replaceAll(match -> {
            String digits = match.group().replace("§x", "").replace("§", "");
            return "<#" + digits + ">";
        });
        text = SECTION_CODE.matcher(text).replaceAll(match -> legacyCodeToTag(match.group(1).charAt(0)));
        text = AMP_HEX.matcher(text).replaceAll("<#$1>");
        text = AMP_CODE.matcher(text).replaceAll(match -> legacyCodeToTag(match.group(1).charAt(0)));
        text = BARE_HEX.matcher(text).replaceAll("<#$1>");
        return text;
    }

    private static String legacyCodeToTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'l' -> "<bold>";
            case 'o' -> "<italic>";
            case 'n' -> "<underlined>";
            case 'm' -> "<strikethrough>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";
            default -> "&" + code;
        };
    }
}
