package me.juancayc.polaroidhomes.menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shipped layouts, built in code rather than read from the jar's menu.yml.
 *
 * <p>These are the fallback a broken menu.yml falls back to, so they must be reachable without
 * reading a file: the reason the fallback exists is that file parsing produced something unusable.
 * They mirror the layouts in {@code src/main/resources/menu.yml} exactly, and both go through the
 * same {@link MenuTemplate#parse} rules, so a mistake here fails the unit tests rather than a
 * server.
 */
public final class DefaultMenuTemplates {

    /** Elements the homes grid renders. */
    public static final Set<MenuElement> HOMES_ELEMENTS = Set.of(
            MenuElement.HOME_SLOT,
            MenuElement.PREVIOUS_PAGE,
            MenuElement.NEXT_PAGE,
            MenuElement.INFO,
            MenuElement.CLOSE,
            MenuElement.EFFECTS,
            MenuElement.FILLER);

    /** Elements the effect category picker renders. */
    public static final Set<MenuElement> EFFECT_PICKER_ELEMENTS = Set.of(
            MenuElement.EFFECT_ANIMATIONS,
            MenuElement.EFFECT_PARTICLES,
            MenuElement.EFFECT_NONE,
            MenuElement.EFFECT_BACK,
            MenuElement.FILLER);

    /** Elements one category's effect list renders. */
    public static final Set<MenuElement> EFFECT_LIST_ELEMENTS = Set.of(
            MenuElement.EFFECT_SLOT,
            MenuElement.PREVIOUS_PAGE,
            MenuElement.NEXT_PAGE,
            MenuElement.EFFECT_NONE,
            MenuElement.EFFECT_BACK,
            MenuElement.FILLER);

    /** Elements the icon picker renders. */
    public static final Set<MenuElement> ICON_ELEMENTS = Set.of(
            MenuElement.ICON_SLOT,
            MenuElement.PREVIOUS_PAGE,
            MenuElement.NEXT_PAGE,
            MenuElement.ICON_RESET,
            MenuElement.ICON_BACK,
            MenuElement.FILLER);

    private static final List<String> HOMES_ROWS = List.of(
            "HHHHHHHHH",
            "HHHHHHHHH",
            "HHHHHHHHH",
            "HHHHHHHHH",
            "HHHHHHHHH",
            "<##EI###>");

    private static final List<String> ICON_ROWS = List.of(
            "OOOOOOOOO",
            "OOOOOOOOO",
            "OOOOOOOOO",
            "OOOOOOOOO",
            "OOOOOOOOO",
            "<##R#B##>");

    /**
     * The category picker: two buttons, centred, over a row carrying unequip and back.
     *
     * <p>Three rows rather than six. The window holds two choices and two chrome buttons, and a
     * six-row window of filler around them would read as a screen that failed to load its contents.
     */
    private static final List<String> EFFECT_PICKER_ROWS = List.of(
            "#########",
            "###A#P###",
            "###N#B###");

    private static final List<String> EFFECT_LIST_ROWS = List.of(
            "EEEEEEEEE",
            "EEEEEEEEE",
            "EEEEEEEEE",
            "EEEEEEEEE",
            "<##N#B##>");

    private DefaultMenuTemplates() {
    }

    /**
     * The shipped homes grid: five rows of homes over a navigation row.
     *
     * <p>Note the close button is not on the default grid. The sixth row carries paging, the
     * counter and the effects button; a close element exists and an operator can place it, but
     * adding one to the default would change what existing servers see.
     *
     * <p>The effects button IS on the default, which does change what existing servers see, and
     * deliberately. It is the only way into the effect catalog, and a feature whose entry point
     * an operator has to discover in a changelog and add by hand is a feature most servers would
     * never switch on. Removing it is one character in menu.yml.
     */
    public static MenuTemplate homes() {
        Map<Character, MenuElement> types = new LinkedHashMap<>();
        Map<Character, MenuTemplate.ElementDefinition> definitions = new LinkedHashMap<>();

        put(types, definitions, 'H', MenuElement.HOME_SLOT, "");
        put(types, definitions, '#', MenuElement.FILLER, "BLACK_STAINED_GLASS_PANE");
        put(types, definitions, '<', MenuElement.PREVIOUS_PAGE, "ARROW");
        put(types, definitions, '>', MenuElement.NEXT_PAGE, "ARROW");
        put(types, definitions, 'I', MenuElement.INFO, "PAPER");
        put(types, definitions, 'E', MenuElement.EFFECTS, "NETHER_STAR");

        return require(MenuTemplate.parse(HOMES_ROWS, definitions, types,
                1, MenuElement.HOME_SLOT, HOMES_ELEMENTS), "homes");
    }

    /** The shipped effect category picker: animations and particles, unequip, back. */
    public static MenuTemplate effectPicker() {
        Map<Character, MenuElement> types = new LinkedHashMap<>();
        Map<Character, MenuTemplate.ElementDefinition> definitions = new LinkedHashMap<>();

        put(types, definitions, '#', MenuElement.FILLER, "BLACK_STAINED_GLASS_PANE");
        put(types, definitions, 'A', MenuElement.EFFECT_ANIMATIONS, "BLAZE_POWDER");
        put(types, definitions, 'P', MenuElement.EFFECT_PARTICLES, "FIREWORK_STAR");
        put(types, definitions, 'N', MenuElement.EFFECT_NONE, "BARRIER");
        put(types, definitions, 'B', MenuElement.EFFECT_BACK, "ARROW");

        // The grid element is the animations button: this window has no content slots, and
        // MenuTemplate requires the named grid element to appear at least once. Naming a button
        // that is always present keeps that rule satisfied without inventing a slot type nothing
        // fills.
        return require(MenuTemplate.parse(EFFECT_PICKER_ROWS, definitions, types,
                1, MenuElement.EFFECT_ANIMATIONS, EFFECT_PICKER_ELEMENTS), "effects");
    }

    /** The shipped effect list: four rows of entries over unequip, back and paging. */
    public static MenuTemplate effectList() {
        Map<Character, MenuElement> types = new LinkedHashMap<>();
        Map<Character, MenuTemplate.ElementDefinition> definitions = new LinkedHashMap<>();

        put(types, definitions, 'E', MenuElement.EFFECT_SLOT, "");
        put(types, definitions, '#', MenuElement.FILLER, "BLACK_STAINED_GLASS_PANE");
        put(types, definitions, '<', MenuElement.PREVIOUS_PAGE, "ARROW");
        put(types, definitions, '>', MenuElement.NEXT_PAGE, "ARROW");
        put(types, definitions, 'N', MenuElement.EFFECT_NONE, "BARRIER");
        put(types, definitions, 'B', MenuElement.EFFECT_BACK, "ARROW");

        return require(MenuTemplate.parse(EFFECT_LIST_ROWS, definitions, types,
                1, MenuElement.EFFECT_SLOT, EFFECT_LIST_ELEMENTS), "effect-list");
    }

    /** The shipped icon picker: five rows of choices over reset, back and paging. */
    public static MenuTemplate icons() {
        Map<Character, MenuElement> types = new LinkedHashMap<>();
        Map<Character, MenuTemplate.ElementDefinition> definitions = new LinkedHashMap<>();

        put(types, definitions, 'O', MenuElement.ICON_SLOT, "");
        put(types, definitions, '#', MenuElement.FILLER, "BLACK_STAINED_GLASS_PANE");
        put(types, definitions, '<', MenuElement.PREVIOUS_PAGE, "ARROW");
        put(types, definitions, '>', MenuElement.NEXT_PAGE, "ARROW");
        put(types, definitions, 'R', MenuElement.ICON_RESET, "BARRIER");
        put(types, definitions, 'B', MenuElement.ICON_BACK, "ARROW");

        return require(MenuTemplate.parse(ICON_ROWS, definitions, types,
                1, MenuElement.ICON_SLOT, ICON_ELEMENTS), "icons");
    }

    private static void put(Map<Character, MenuElement> types,
                            Map<Character, MenuTemplate.ElementDefinition> definitions,
                            char symbol,
                            MenuElement element,
                            String item) {
        types.put(symbol, element);
        // No name or lore: the shipped layout keeps taking those from the language file, so an
        // existing translation keeps working and a fallback never prints English over it.
        definitions.put(symbol,
                new MenuTemplate.ElementDefinition(item, null, List.of(), null, false));
    }

    /**
     * Unwraps a parse of a layout written in this file.
     *
     * <p>An invalid default is a programming error, not an operator one: there is nothing further
     * to fall back to, so it fails loudly and the unit tests catch it before a release.
     */
    private static MenuTemplate require(MenuTemplate.Result result, String name) {
        if (!result.isValid()) {
            throw new IllegalStateException(
                    "The built-in " + name + " layout is invalid: " + result.errors());
        }
        return result.template();
    }
}
