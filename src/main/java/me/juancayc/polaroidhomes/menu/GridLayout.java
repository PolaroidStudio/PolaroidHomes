package me.juancayc.polaroidhomes.menu;

/**
 * Paging arithmetic for the homes menu.
 *
 * <p>Deliberately Bukkit-free. The failure this class prevents is a server whose EssentialsX
 * config declares an enormous or effectively unlimited tier, which would otherwise ask the menu to
 * render more slots than the layout can page through. Keeping the arithmetic in a plain type means
 * the rule runs in a unit test on every build rather than depending on somebody remembering to try
 * it against a misconfigured server.
 *
 * <p>Where each slot sits is {@link MenuTemplate}'s business, not this class's: an operator's
 * layout decides the positions, and this decides how many of them a page shows and how many pages
 * there are.
 *
 * @param rows            rows of the window, 1-6
 * @param visibleSlots    total home slots drawn across all pages
 * @param slotsPerPage    home slots the layout places on one page
 */
public record GridLayout(int rows, int visibleSlots, int slotsPerPage) {

    /**
     * Sizes the grid from a template.
     *
     * <p>The template, not this class, decides how many content slots a page has and how big the
     * window is, so the only arithmetic left here is the paging and the cap. The cap is clamped to
     * the template's own content-slot count: a {@code max-displayed-slots} smaller than one page
     * would otherwise leave declared home slots permanently blank.
     *
     * @param template          the validated layout
     * @param highestTier       the largest home limit any EssentialsX group grants
     * @param maxDisplayedSlots {@code max-displayed-slots} from menu.yml
     */
    public static GridLayout of(MenuTemplate template, int highestTier, int maxDisplayedSlots) {
        int slotsPerPage = template.slotsPerPage();
        int requested = Math.max(highestTier, slotsPerPage);
        // No upper clamp against the window size: a template that pages can legitimately show more
        // slots in total than one window holds, and validation already proved it carries the paging
        // buttons that requires. Clamping here would quietly cut a rank's slots off instead.
        int cap = Math.max(maxDisplayedSlots, slotsPerPage);
        int visible = Math.min(requested, cap);
        return new GridLayout(template.rows(), visible, slotsPerPage);
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
