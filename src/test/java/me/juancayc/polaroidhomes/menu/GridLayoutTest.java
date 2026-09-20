package me.juancayc.polaroidhomes.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grid is sized from a value an operator controls in another plugin's config, capped by one they
 * control in menu.yml. The failure mode is a server that refuses to open the menu, or a rank whose
 * slots silently vanish, so these run headless on every build rather than depending on somebody
 * remembering to try a misconfigured EssentialsX.
 */
class GridLayoutTest {

    /** Builds a template from row strings, with the standard character set declared. */
    private static MenuTemplate template(int maxTotalSlots, String... rows) {
        Map<Character, MenuElement> types = new LinkedHashMap<>();
        Map<Character, MenuTemplate.ElementDefinition> definitions = new LinkedHashMap<>();
        Map<Character, MenuElement> all = Map.of(
                'H', MenuElement.HOME_SLOT,
                '#', MenuElement.FILLER,
                '<', MenuElement.PREVIOUS_PAGE,
                '>', MenuElement.NEXT_PAGE,
                'I', MenuElement.INFO);
        // Only what the rows actually use, so the unused-element warning never fires here and a
        // failure is always about the sizing rule under test.
        for (String row : rows) {
            for (char symbol : row.toCharArray()) {
                if (all.containsKey(symbol)) {
                    types.put(symbol, all.get(symbol));
                    definitions.put(symbol,
                            new MenuTemplate.ElementDefinition("", null, List.of(), null, false));
                }
            }
        }
        MenuTemplate.Result result = MenuTemplate.parse(List.of(rows), definitions, types,
                maxTotalSlots, MenuElement.HOME_SLOT, DefaultMenuTemplates.HOMES_ELEMENTS);
        assertTrue(result.isValid(), () -> "test fixture is not a valid layout: " + result.errors());
        return result.template();
    }

    @Test
    @DisplayName("an absurd tier is capped by max-displayed-slots, not honoured")
    void capsAbsurdTier() {
        GridLayout layout = GridLayout.of(DefaultMenuTemplates.homes(), 9999, 45);

        assertEquals(45, layout.visibleSlots());
        assertEquals(54, layout.inventorySize());
        assertEquals(1, layout.pageCount());
    }

    @Test
    @DisplayName("a tier below the page size still fills one page")
    void smallTierStillFillsAPage() {
        // Three homes on a six-row window draws forty-five slots, not three: the locked ones are
        // what tell the player a rank exists above them.
        GridLayout layout = GridLayout.of(DefaultMenuTemplates.homes(), 3, 45);

        assertEquals(45, layout.visibleSlots());
        assertEquals(1, layout.pageCount());
    }

    @Test
    @DisplayName("a zero or negative tier does not produce an empty window")
    void nonPositiveTierStillDraws() {
        GridLayout layout = GridLayout.of(DefaultMenuTemplates.homes(), 0, 45);

        assertEquals(45, layout.visibleSlots());
        assertEquals(1, layout.pageCount());
    }

    @Test
    @DisplayName("the window is sized from the template, not from a separate row count")
    void windowIsSizedFromTheTemplate() {
        assertEquals(18, GridLayout.of(template(9, "HHHHHHHHH", "####I####"), 10, 9)
                .inventorySize());
        assertEquals(9, GridLayout.of(template(9, "HHHHHHHHH"), 10, 9).inventorySize());
        assertEquals(54, GridLayout.of(DefaultMenuTemplates.homes(), 10, 45).inventorySize());
    }

    @Test
    @DisplayName("paging covers every visible slot exactly once")
    void pagingCoversEverySlot() {
        // Eighteen content slots and forty visible, so three pages, the last of which is short.
        MenuTemplate template = template(45, "HHHHHHHHH", "HHHHHHHHH", "<###I###>");
        GridLayout layout = GridLayout.of(template, 40, 45);

        assertEquals(18, layout.slotsPerPage());
        assertEquals(40, layout.visibleSlots());
        assertEquals(3, layout.pageCount());

        int total = 0;
        for (int page = 0; page < layout.pageCount(); page++) {
            total += layout.slotsOnPage(page);
        }
        assertEquals(layout.visibleSlots(), total);
    }

    @Test
    @DisplayName("a page past the end draws nothing rather than a negative count")
    void pastTheEndIsEmpty() {
        GridLayout layout = GridLayout.of(DefaultMenuTemplates.homes(), 45, 45);

        assertEquals(0, layout.slotsOnPage(5));
    }

    @Test
    @DisplayName("a paging template may show more slots in total than one window holds")
    void aPagingTemplateMayExceedOneWindow() {
        // Nothing clamps the total to a single window any more: the template was validated to carry
        // paging buttons, so cutting a rank's slots off here would be the bug, not the safeguard.
        GridLayout layout = GridLayout.of(template(90, "HHHHHHHHH", "<###I###>"), 90, 90);

        assertEquals(9, layout.slotsPerPage());
        assertEquals(90, layout.visibleSlots());
        assertEquals(10, layout.pageCount());
        assertEquals(18, layout.inventorySize());
    }

    @Test
    @DisplayName("a cap below one page still fills the page the operator drew")
    void aCapBelowOnePageStillFillsThePage() {
        // Otherwise home slots the operator explicitly placed would render blank forever.
        GridLayout layout = GridLayout.of(DefaultMenuTemplates.homes(), 60, 10);

        assertEquals(45, layout.visibleSlots());
        assertEquals(1, layout.pageCount());
    }
}
