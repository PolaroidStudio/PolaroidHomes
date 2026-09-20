package me.juancayc.polaroidhomes.menu;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed, validated character-template layout for one menu.
 *
 * <p>Bukkit-free on purpose, for the same reason {@link GridLayout} is: the values that decide
 * whether a window can even be constructed come from a file an operator edits, so the rules that
 * reject a broken one have to run in a unit test on every build rather than depend on somebody
 * remembering to try a typo against a live server.
 *
 * <h2>The scheme</h2>
 * <pre>
 * rows:
 *   - "#########"
 *   - "#HHHHHHH#"
 *   - "#HHHHHHH#"
 *   - "#########"
 *   - "&lt;##.#I#..&gt;"
 *   - "####X####"
 * elements:
 *   '#': { type: filler,        item: BLACK_STAINED_GLASS_PANE }
 *   'H': { type: home-slot }
 *   '&lt;': { type: previous-page, item: ARROW, name: "...", lore: [...] }
 * </pre>
 *
 * <p>One row per inventory row, exactly nine characters each, 1-6 rows. Every character other than
 * a space must have an entry under {@code elements}; a space is always an empty slot with no item
 * and no handler, so a layout does not have to declare a filler at all.
 *
 * @param rows        row count, 1-6
 * @param slots       raw slot to element, only for slots a template actually places
 * @param homeSlots   the raw slots marked with the grid element, in reading order
 * @param definitions the declared appearance of each element, by element
 */
public record MenuTemplate(int rows,
                           Map<Integer, MenuElement> slots,
                           List<Integer> homeSlots,
                           Map<MenuElement, ElementDefinition> definitions) {

    /** Bukkit refuses a chest inventory wider or narrower than nine columns. */
    public static final int COLUMNS = 9;

    /** Bukkit refuses a chest inventory larger than six rows. */
    public static final int MAX_ROWS = 6;

    /** A character left blank: no item, no handler. */
    public static final char EMPTY = ' ';

    /**
     * How one element looks.
     *
     * @param item      item reference, resolved through the existing item layer
     * @param name      MiniMessage display name, or null to keep the item's own
     * @param lore      MiniMessage lore lines, possibly empty
     * @param modelData custom model data, or null to leave it unset
     * @param glow      true to add the enchantment glint
     */
    public record ElementDefinition(String item,
                                    @Nullable String name,
                                    List<String> lore,
                                    @Nullable Integer modelData,
                                    boolean glow) {
    }

    /** Size in slots of the Bukkit inventory this template needs. */
    public int inventorySize() {
        return rows * COLUMNS;
    }

    /** How many home slots one page draws. Never zero: a valid template has at least one. */
    public int slotsPerPage() {
        return homeSlots.size();
    }

    /** True when the template placed this element at least once. */
    public boolean has(MenuElement element) {
        return slots.containsValue(element);
    }

    /** The declared appearance of an element, or null when the template never declared it. */
    public @Nullable ElementDefinition definition(MenuElement element) {
        return definitions.get(element);
    }

    /**
     * Parses and validates a template.
     *
     * <p>Never throws and never returns null: the result is either a template or a list of
     * problems the caller logs before falling back to the shipped default. An operator whose typo
     * disabled their menu learns nothing from a stack trace.
     *
     * @param rawRows        the {@code rows} strings, in order
     * @param rawElements    character to declaration, as read from {@code elements}
     * @param maxTotalSlots  how many home slots the menu needs across all pages, which decides
     *                       whether pagination buttons are required; pass 1 for a menu that never
     *                       pages, such as the icon picker where the count is not known up front
     * @param allowed        the elements this menu type understands
     */
    public static Result parse(List<String> rawRows,
                               Map<Character, ElementDefinition> rawElements,
                               Map<Character, MenuElement> rawTypes,
                               int maxTotalSlots,
                               MenuElement gridElement,
                               java.util.Set<MenuElement> allowed) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (rawRows == null || rawRows.isEmpty()) {
            errors.add("'rows' is missing or empty. A menu needs between 1 and " + MAX_ROWS
                    + " row strings.");
            return new Result(null, errors, warnings);
        }
        if (rawRows.size() > MAX_ROWS) {
            errors.add("'rows' declares " + rawRows.size() + " rows; Bukkit accepts at most "
                    + MAX_ROWS + " (" + MAX_ROWS * COLUMNS + " slots).");
            return new Result(null, errors, warnings);
        }

        for (int index = 0; index < rawRows.size(); index++) {
            String row = rawRows.get(index);
            if (row == null) {
                errors.add("rows[" + index + "] is empty. Every row must be exactly " + COLUMNS
                        + " characters long.");
            } else if (row.length() != COLUMNS) {
                errors.add("rows[" + index + "] is " + row.length() + " characters long ('" + row
                        + "'); every row must be exactly " + COLUMNS + ".");
            }
        }
        if (!errors.isEmpty()) {
            return new Result(null, errors, warnings);
        }

        Map<Integer, MenuElement> slots = new LinkedHashMap<>();
        List<Integer> homeSlots = new ArrayList<>();
        java.util.Set<Character> usedChars = new java.util.LinkedHashSet<>();

        for (int row = 0; row < rawRows.size(); row++) {
            String line = rawRows.get(row);
            for (int column = 0; column < COLUMNS; column++) {
                char symbol = line.charAt(column);
                if (symbol == EMPTY) {
                    continue;
                }
                usedChars.add(symbol);
                MenuElement element = rawTypes.get(symbol);
                if (element == null) {
                    errors.add("rows[" + row + "] uses the character '" + symbol
                            + "' at column " + column
                            + " but no entry under 'elements' declares it.");
                    continue;
                }
                if (!allowed.contains(element)) {
                    errors.add("'elements." + symbol + ".type' is '" + element.key()
                            + "', which this menu does not render.");
                    continue;
                }
                int slot = row * COLUMNS + column;
                slots.put(slot, element);
                if (element == gridElement) {
                    homeSlots.add(slot);
                }
            }
        }

        for (Character declared : rawTypes.keySet()) {
            if (!usedChars.contains(declared)) {
                // Harmless — nothing renders it — but an element declared and never placed is
                // almost always a row string the operator forgot to update.
                warnings.add("'elements." + declared
                        + "' is declared but never appears in any row, so it is never drawn.");
            }
        }

        if (homeSlots.isEmpty()) {
            errors.add("no row places the '" + gridElement.key()
                    + "' element, so the menu would show no content at all.");
        }

        if (!errors.isEmpty()) {
            return new Result(null, errors, warnings);
        }

        validatePagination(slots, homeSlots.size(), maxTotalSlots, errors, warnings);
        if (!errors.isEmpty()) {
            return new Result(null, errors, warnings);
        }

        Map<MenuElement, ElementDefinition> definitions = new EnumMap<>(MenuElement.class);
        for (Map.Entry<Character, MenuElement> entry : rawTypes.entrySet()) {
            ElementDefinition definition = rawElements.get(entry.getKey());
            if (definition != null) {
                // Last declaration wins when two characters name the same element. Both would draw
                // the same button anyway, so the only thing at stake is which appearance is used.
                definitions.put(entry.getValue(), definition);
            }
        }

        return new Result(
                new MenuTemplate(rawRows.size(), Map.copyOf(slots), List.copyOf(homeSlots),
                        Map.copyOf(definitions)),
                errors, warnings);
    }

    /**
     * The pagination interaction, which is the one an operator gets wrong without noticing.
     *
     * <p>A template with <em>no</em> paging buttons whose single page cannot hold everything the
     * menu has to show silently hides content: the slots past the last one exist and are
     * unreachable, which looks like data loss rather than like a configuration mistake. That is an
     * error.
     *
     * <p>The reverse is deliberately not even a warning. Paging buttons on a layout that fits the
     * cap on one page are simply not drawn, and the layout is still correct: how many slots a
     * player actually sees comes from their EssentialsX tier, and the cap is only the ceiling. A
     * warning there would fire on the shipped default.
     *
     * <p>One button without the other is an error either way, because it is never what an operator
     * meant: a next-page with no previous-page strands the player on page two.
     */
    private static void validatePagination(Map<Integer, MenuElement> slots,
                                           int slotsPerPage,
                                           int maxTotalSlots,
                                           List<String> errors,
                                           List<String> warnings) {
        boolean hasPrevious = slots.containsValue(MenuElement.PREVIOUS_PAGE);
        boolean hasNext = slots.containsValue(MenuElement.NEXT_PAGE);
        boolean needsPaging = maxTotalSlots > slotsPerPage;

        if (needsPaging && !(hasPrevious && hasNext)) {
            errors.add("the layout has " + slotsPerPage + " content slot(s) per page but the menu "
                    + "needs to show up to " + maxTotalSlots
                    + ", so it must page. Place both a 'previous-page' and a 'next-page' element, "
                    + "or give the layout at least " + maxTotalSlots + " content slots.");
            return;
        }
        if (hasPrevious != hasNext) {
            errors.add("the layout places only "
                    + (hasPrevious ? "'previous-page'" : "'next-page'")
                    + ". Paging needs both buttons, or neither.");
            return;
        }
    }

    /**
     * The outcome of a parse: a template, or the reasons there is none.
     *
     * @param template null when the layout was rejected
     * @param errors   why it was rejected; empty on success
     * @param warnings mistakes that do not break the menu; may be non-empty on success
     */
    public record Result(@Nullable MenuTemplate template,
                         List<String> errors,
                         List<String> warnings) {

        public boolean isValid() {
            return template != null;
        }
    }
}
