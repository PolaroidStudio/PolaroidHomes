package me.juancayc.polaroidhomes.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grid is sized from a value an operator controls in another plugin's config, so the failure
 * mode here is a server that refuses to open the menu at all. These run headless on every build
 * rather than depending on somebody remembering to try a misconfigured EssentialsX.
 */
class GridLayoutTest {

    @Test
    @DisplayName("an absurd tier is capped by max-displayed-slots, not honoured")
    void capsAbsurdTier() {
        GridLayout layout = GridLayout.of(6, 9999, 45);

        assertEquals(45, layout.visibleSlots());
        assertEquals(54, layout.inventorySize());
    }

    @Test
    @DisplayName("the cap itself cannot exceed the usable area of the window")
    void capIsClampedToUsableArea() {
        // An operator who raises max-displayed-slots past what a 6-row window can hold must not get
        // a grid that promises slots the inventory has no room for.
        GridLayout layout = GridLayout.of(6, 9999, 500);

        assertEquals(45, layout.visibleSlots());
        assertTrue(layout.visibleSlots() <= layout.inventorySize() - 9);
    }

    @Test
    @DisplayName("a tier below the page size still fills one page")
    void smallTierStillFillsAPage() {
        // Three homes on a six-row window draws forty-five slots, not three: the locked ones are
        // what tell the player a rank exists above them.
        GridLayout layout = GridLayout.of(6, 3, 45);

        assertEquals(45, layout.visibleSlots());
        assertEquals(1, layout.pageCount());
    }

    @Test
    @DisplayName("rows are clamped to what Bukkit accepts")
    void clampsRows() {
        assertEquals(54, GridLayout.of(99, 10, 45).inventorySize());
        assertEquals(18, GridLayout.of(1, 10, 45).inventorySize());
    }

    @Test
    @DisplayName("paging covers every visible slot exactly once")
    void pagingCoversEverySlot() {
        // Three rows: eighteen usable slots above the navigation row, so forty visible slots need
        // three pages, the last of which is short.
        GridLayout layout = GridLayout.of(3, 40, 45);

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
        GridLayout layout = GridLayout.of(6, 45, 45);

        assertEquals(0, layout.slotsOnPage(5));
    }

    @Test
    @DisplayName("navigation slots sit in the last row and never collide")
    void navigationSlotsAreDistinctAndInTheLastRow() {
        GridLayout layout = GridLayout.of(6, 45, 45);
        int firstNavigationSlot = layout.inventorySize() - 9;

        assertTrue(layout.previousPageSlot() >= firstNavigationSlot);
        assertTrue(layout.nextPageSlot() >= firstNavigationSlot);
        assertTrue(layout.infoSlot() >= firstNavigationSlot);
        assertTrue(layout.closeSlot() >= firstNavigationSlot);

        assertTrue(layout.closeSlot() < layout.inventorySize());
        assertEquals(4, java.util.Set.of(
                layout.previousPageSlot(),
                layout.nextPageSlot(),
                layout.infoSlot(),
                layout.closeSlot()).size());
    }

    @Test
    @DisplayName("a zero or negative tier does not produce an empty window")
    void nonPositiveTierStillDraws() {
        GridLayout layout = GridLayout.of(6, 0, 45);

        assertEquals(45, layout.visibleSlots());
        assertEquals(1, layout.pageCount());
    }
}
