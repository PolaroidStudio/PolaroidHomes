package me.juancayc.polaroidhomes.menu;

/**
 * Grid sizing for the homes menu.
 *
 * <p>Deliberately Bukkit-free. The failure this class prevents is a server whose EssentialsX
 * config declares an enormous or effectively unlimited tier, which would otherwise ask the menu to
 * render more slots than an inventory can hold. Keeping the arithmetic in a plain type means the
 * rule runs in a unit test on every build rather than depending on somebody remembering to try it
 * against a misconfigured server.
 *
 * @param rows            rows of the window, 1-6
 * @param visibleSlots    total home slots drawn across all pages
 * @param slotsPerPage    home slots on one page, the usable area above the navigation row
 */
public record GridLayout(int rows, int visibleSlots, int slotsPerPage) {

    /** Bukkit refuses any chest inventory larger than this. */
    public static final int MAX_INVENTORY_SIZE = 54;

    /** The bottom row is reserved for navigation chrome, so it never holds a home. */
    public static final int NAVIGATION_ROWS = 1;

    /**
     * Sizes the grid.
     *
     * @param configuredRows      {@code gui.rows}
     * @param highestTier         the largest home limit any EssentialsX group grants
     * @param maxDisplayedSlots   {@code gui.max-displayed-slots}
     */
    public static GridLayout of(int configuredRows, int highestTier, int maxDisplayedSlots) {
        int rows = clamp(configuredRows, 2, MAX_INVENTORY_SIZE / 9);
        int slotsPerPage = (rows - NAVIGATION_ROWS) * 9;

        // A tier of zero or below is still one page of locked slots: showing an empty window tells
        // the player nothing, while a row of padlocks tells them a rank unlocks it.
        int requested = Math.max(highestTier, slotsPerPage);
        // The cap is the whole point of the class. It is itself clamped to the usable area of the
        // window so a max-displayed-slots larger than the grid cannot promise a page that will not
        // fit.
        int cap = clamp(maxDisplayedSlots, slotsPerPage, MAX_INVENTORY_SIZE - (NAVIGATION_ROWS * 9));
        int visible = Math.min(requested, cap);

        return new GridLayout(rows, visible, slotsPerPage);
    }

    /** Size in slots of the Bukkit inventory this layout needs. */
    public int inventorySize() {
        return rows * 9;
    }

    /** Pages needed to show every visible slot. Always at least one. */
    public int pageCount() {
        return Math.max(1, (int) Math.ceil(visibleSlots / (double) slotsPerPage));
    }

    /** The global slot index that page {@code page} (zero-based) starts at. */
    public int firstIndexOfPage(int page) {
        return page * slotsPerPage;
    }

    /** How many home slots page {@code page} (zero-based) actually draws. */
    public int slotsOnPage(int page) {
        int remaining = visibleSlots - firstIndexOfPage(page);
        return clamp(remaining, 0, slotsPerPage);
    }

    /** Raw slot of the previous-page button. */
    public int previousPageSlot() {
        return inventorySize() - 9;
    }

    /** Raw slot of the info button, centred in the navigation row. */
    public int infoSlot() {
        return inventorySize() - 5;
    }

    /** Raw slot of the close button. */
    public int closeSlot() {
        return inventorySize() - 1;
    }

    /** Raw slot of the next-page button. */
    public int nextPageSlot() {
        return inventorySize() - 9 + 8 - 1;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
