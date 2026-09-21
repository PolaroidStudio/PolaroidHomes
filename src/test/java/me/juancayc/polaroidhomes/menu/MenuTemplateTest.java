package me.juancayc.polaroidhomes.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layout now comes from a file an operator edits, so every way they can break it has to be
 * rejected with a message that names what is wrong. These run headless on every build: the failure
 * these rules prevent is a window that throws on open or silently hides half a player's homes, and
 * neither shows up until somebody opens the menu on a live server.
 */
class MenuTemplateTest {

    private static final Set<MenuElement> HOMES = DefaultMenuTemplates.HOMES_ELEMENTS;

    /** A builder for the element declarations, so each case only writes the part it is testing. */
    private static final class Elements {
        private final Map<Character, MenuElement> types = new LinkedHashMap<>();
        private final Map<Character, MenuTemplate.ElementDefinition> definitions =
                new LinkedHashMap<>();

        Elements with(char symbol, MenuElement element) {
            return with(symbol, element, "");
        }

        Elements with(char symbol, MenuElement element, String item) {
            types.put(symbol, element);
            definitions.put(symbol,
                    new MenuTemplate.ElementDefinition(item, null, List.of(), null, false));
            return this;
        }
    }

    private static Elements standard() {
        return new Elements()
                .with('H', MenuElement.HOME_SLOT)
                .with('#', MenuElement.FILLER, "BLACK_STAINED_GLASS_PANE")
                .with('<', MenuElement.PREVIOUS_PAGE, "ARROW")
                .with('>', MenuElement.NEXT_PAGE, "ARROW")
                .with('I', MenuElement.INFO, "PAPER")
                .with('X', MenuElement.CLOSE, "BARRIER");
    }

    private static MenuTemplate.Result parse(List<String> rows, Elements elements, int maxSlots) {
        return MenuTemplate.parse(rows, elements.definitions, elements.types, maxSlots,
                MenuElement.HOME_SLOT, HOMES);
    }

    // --- the happy path -----------------------------------------------------

    @Test
    @DisplayName("a valid layout maps every character to the slot it sits in")
    void validLayoutProducesTheExpectedSlotMap() {
        MenuTemplate.Result result = parse(List.of(
                "#########",
                "#HHHHHHH#",
                "<##I##X#>"), standard(), 7);

        assertTrue(result.isValid(), () -> "unexpectedly rejected: " + result.errors());
        MenuTemplate template = result.template();

        assertEquals(3, template.rows());
        assertEquals(27, template.inventorySize());
        // Row 1, columns 1-7.
        assertEquals(List.of(10, 11, 12, 13, 14, 15, 16), template.homeSlots());
        assertEquals(7, template.slotsPerPage());
        assertEquals(MenuElement.PREVIOUS_PAGE, template.slots().get(18));
        assertEquals(MenuElement.INFO, template.slots().get(21));
        assertEquals(MenuElement.CLOSE, template.slots().get(24));
        assertEquals(MenuElement.NEXT_PAGE, template.slots().get(26));
        assertEquals(MenuElement.FILLER, template.slots().get(0));
    }

    @Test
    @DisplayName("a space is an empty slot rather than an undeclared character")
    void spacesAreEmptySlots() {
        MenuTemplate.Result result = parse(List.of("  HHHHH  "), standard(), 5);

        assertTrue(result.isValid(), () -> "unexpectedly rejected: " + result.errors());
        assertFalse(result.template().slots().containsKey(0),
                "a space must place nothing at all, not a filler");
        assertEquals(List.of(2, 3, 4, 5, 6), result.template().homeSlots());
    }

    @Test
    @DisplayName("home slots are filled in reading order, not in declaration order")
    void homeSlotsAreInReadingOrder() {
        MenuTemplate.Result result = parse(List.of(
                "####H####",
                "H#######H"), standard(), 3);

        assertTrue(result.isValid());
        assertEquals(List.of(4, 9, 17), result.template().homeSlots());
    }

    @Test
    @DisplayName("declared name, lore, model data and glow survive the parse")
    void appearanceSurvivesTheParse() {
        Map<Character, MenuElement> types = new LinkedHashMap<>();
        Map<Character, MenuTemplate.ElementDefinition> definitions = new LinkedHashMap<>();
        types.put('H', MenuElement.HOME_SLOT);
        definitions.put('H',
                new MenuTemplate.ElementDefinition("", null, List.of(), null, false));
        types.put('I', MenuElement.INFO);
        definitions.put('I', new MenuTemplate.ElementDefinition(
                "nexo:counter", "<#289bd0>ᴄᴏᴜɴᴛ", List.of("", "<#95d027>▸ ʟᴏᴏᴋ"), 7, true));

        MenuTemplate.Result result = MenuTemplate.parse(List.of("HHHHIHHHH"),
                definitions, types, 8, MenuElement.HOME_SLOT, HOMES);

        assertTrue(result.isValid(), () -> "unexpectedly rejected: " + result.errors());
        MenuTemplate.ElementDefinition info = result.template().definition(MenuElement.INFO);
        assertEquals("nexo:counter", info.item());
        assertEquals("<#289bd0>ᴄᴏᴜɴᴛ", info.name());
        assertEquals(2, info.lore().size());
        assertEquals(Integer.valueOf(7), info.modelData());
        assertTrue(info.glow());
    }

    // --- the rejection matrix -----------------------------------------------

    @Test
    @DisplayName("more than six rows is rejected, naming the row count")
    void tooManyRowsIsRejected() {
        MenuTemplate.Result result = parse(List.of(
                "HHHHHHHHH", "HHHHHHHHH", "HHHHHHHHH",
                "HHHHHHHHH", "HHHHHHHHH", "HHHHHHHHH",
                "HHHHHHHHH"), standard(), 45);

        assertFalse(result.isValid());
        assertTrue(result.errors().getFirst().contains("7 rows"),
                () -> "the message must name the row count: " + result.errors());
    }

    @Test
    @DisplayName("zero rows is rejected")
    void noRowsIsRejected() {
        MenuTemplate.Result result = parse(List.of(), standard(), 45);

        assertFalse(result.isValid());
        assertTrue(result.errors().getFirst().contains("'rows'"));
    }

    @Test
    @DisplayName("a row that is not exactly nine characters is rejected, naming its index")
    void wrongRowLengthIsRejected() {
        MenuTemplate.Result result = parse(List.of(
                "HHHHHHHHH",
                "HHHHHHHH",
                "<###I###>"), standard(), 17);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("rows[1]")),
                () -> "the message must name the offending row: " + result.errors());
    }

    @Test
    @DisplayName("a layout with no home slot is rejected rather than opening an empty window")
    void noHomeSlotIsRejected() {
        MenuTemplate.Result result = parse(List.of("<###I###X"), standard(), 1);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("home-slot")),
                () -> result.errors().toString());
    }

    @Test
    @DisplayName("a character with no declared element is rejected, naming the character")
    void undeclaredCharacterIsRejected() {
        MenuTemplate.Result result = parse(List.of("HHHH?HHHH"), standard(), 8);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("'?'")),
                () -> "the message must name the character: " + result.errors());
    }

    @Test
    @DisplayName("an element the menu does not render is rejected")
    void foreignElementIsRejected() {
        Elements elements = standard().with('R', MenuElement.ICON_RESET, "BARRIER");
        MenuTemplate.Result result = parse(List.of("HHHHRHHHH"), elements, 8);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("icon-reset")),
                () -> result.errors().toString());
    }

    @Test
    @DisplayName("an element declared but never placed is a warning, not a rejection")
    void unusedElementIsOnlyWarned() {
        // Harmless: nothing renders it. Almost always a row string the operator forgot to update,
        // which is exactly what a warning is for.
        MenuTemplate.Result result = parse(List.of("HHHHHHHHH"), standard(), 9);

        assertTrue(result.isValid(), () -> "unexpectedly rejected: " + result.errors());
        assertTrue(result.warnings().stream()
                        .anyMatch(warning -> warning.contains("elements.X")),
                () -> "the warning must name each unused element: " + result.warnings());
    }

    // --- the pagination interaction -----------------------------------------

    @Test
    @DisplayName("a layout that must page without paging buttons is rejected")
    void missingPaginationForMultiplePagesIsRejected() {
        // Nine content slots but a cap of forty-five: without buttons, thirty-six slots exist and
        // are unreachable, which reads as data loss rather than as a mistake in a file.
        MenuTemplate.Result result = parse(List.of("HHHHHHHHH", "####I####"), standard(), 45);

        assertFalse(result.isValid());
        String error = result.errors().getFirst();
        assertTrue(error.contains("9") && error.contains("45"),
                () -> "the message must name both numbers: " + error);
        assertTrue(error.contains("previous-page") && error.contains("next-page"),
                () -> "the message must name the fix: " + error);
    }

    @Test
    @DisplayName("a layout that must page and has both buttons is accepted")
    void paginationForMultiplePagesIsAccepted() {
        MenuTemplate.Result result = parse(List.of("HHHHHHHHH", "<###I###>"), standard(), 45);

        assertTrue(result.isValid(), () -> "unexpectedly rejected: " + result.errors());
        assertEquals(9, result.template().slotsPerPage());
    }

    @Test
    @DisplayName("paging buttons on a layout that never pages are accepted silently")
    void paginationOnASinglePageLayoutIsFine() {
        // How many slots a player sees comes from their rank or their own limit; the cap is only a
        // ceiling. Warning here would fire on the shipped default.
        Elements placed = new Elements()
                .with('H', MenuElement.HOME_SLOT)
                .with('#', MenuElement.FILLER, "BLACK_STAINED_GLASS_PANE")
                .with('<', MenuElement.PREVIOUS_PAGE, "ARROW")
                .with('>', MenuElement.NEXT_PAGE, "ARROW")
                .with('I', MenuElement.INFO, "PAPER");
        MenuTemplate.Result result = parse(List.of(
                "HHHHHHHHH", "HHHHHHHHH", "HHHHHHHHH",
                "HHHHHHHHH", "HHHHHHHHH", "<###I###>"), placed, 45);

        assertTrue(result.isValid(), () -> "unexpectedly rejected: " + result.errors());
        assertEquals(45, result.template().slotsPerPage());
        assertTrue(result.warnings().isEmpty(), () -> result.warnings().toString());
    }

    @Test
    @DisplayName("only one of the two paging buttons is rejected")
    void halfOfPaginationIsRejected() {
        MenuTemplate.Result result = parse(List.of("HHHHHHHHH", "<###I####"), standard(), 45);

        assertFalse(result.isValid());
        assertTrue(result.errors().getFirst().contains("next-page"),
                () -> result.errors().toString());
    }

    @Test
    @DisplayName("a cap equal to one page needs no paging buttons")
    void capEqualToOnePageNeedsNoPaging() {
        MenuTemplate.Result result = parse(List.of("HHHHHHHHH", "####I####"), standard(), 9);

        assertTrue(result.isValid(), () -> "unexpectedly rejected: " + result.errors());
    }

    // --- the shipped defaults ----------------------------------------------

    @Test
    @DisplayName("the built-in layouts are valid, so the fallback always has somewhere to land")
    void shippedDefaultsAreValid() {
        MenuTemplate homes = DefaultMenuTemplates.homes();
        assertEquals(6, homes.rows());
        assertEquals(54, homes.inventorySize());
        assertEquals(45, homes.slotsPerPage());
        assertTrue(homes.has(MenuElement.PREVIOUS_PAGE));
        assertTrue(homes.has(MenuElement.NEXT_PAGE));
        assertTrue(homes.has(MenuElement.INFO));

        MenuTemplate icons = DefaultMenuTemplates.icons();
        assertEquals(54, icons.inventorySize());
        assertEquals(45, icons.slotsPerPage());
        assertTrue(icons.has(MenuElement.ICON_RESET));
        assertTrue(icons.has(MenuElement.ICON_BACK));
    }

    @Test
    @DisplayName("the default grid and the default picker do not put content under their chrome")
    void shippedDefaultsKeepContentOffTheChromeRow() {
        for (MenuTemplate template : List.of(
                DefaultMenuTemplates.homes(), DefaultMenuTemplates.icons())) {
            int firstChromeSlot = template.inventorySize() - MenuTemplate.COLUMNS;
            for (int slot : template.homeSlots()) {
                assertTrue(slot < firstChromeSlot,
                        "content slot " + slot + " overlaps the navigation row");
            }
        }
    }

    @Test
    @DisplayName("an element name is read case- and separator-insensitively")
    void elementNamesAreForgiving() {
        assertEquals(MenuElement.HOME_SLOT, MenuElement.byKey("HOME_SLOT"));
        assertEquals(MenuElement.PREVIOUS_PAGE, MenuElement.byKey(" previous-page "));
        assertEquals(MenuElement.ICON_BACK, MenuElement.byKey("icon_back"));
        assertEquals(null, MenuElement.byKey("teleport-everyone"));
    }
}
