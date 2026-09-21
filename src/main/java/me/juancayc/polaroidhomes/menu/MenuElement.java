package me.juancayc.polaroidhomes.menu;

/**
 * The elements a menu template can place.
 *
 * <p>Deliberately a closed set rather than a free string: every value here corresponds to a
 * behaviour the renderer actually implements. An operator who could name an arbitrary element
 * would get a slot that draws an item and does nothing, which reads as a broken menu rather than
 * as a configuration mistake.
 *
 * <p>Bukkit-free so the whole template layer, including validation, runs in a unit test.
 */
public enum MenuElement {

    /**
     * Where the homes go. Positions marked with this element are filled, in reading order, with
     * homes, then unlocked-but-empty slots, then locked slots.
     */
    HOME_SLOT("home-slot"),

    /** Previous page button. Drawn only when a previous page exists. */
    PREVIOUS_PAGE("previous-page"),

    /** Next page button. Drawn only when a next page exists. */
    NEXT_PAGE("next-page"),

    /** The counter: homes used, limit, current page. Carries no handler. */
    INFO("info"),

    /** Closes the window. */
    CLOSE("close"),

    /** Background. Carries no handler, and its tooltip is hidden. */
    FILLER("filler"),

    /** Resets a home's icon back to the default. Icon picker only. */
    ICON_RESET("icon-reset"),

    /** Returns from the icon picker to the homes grid. Icon picker only. */
    ICON_BACK("icon-back"),

    /** Where the icon choices go in the picker, the picker's equivalent of {@link #HOME_SLOT}. */
    ICON_SLOT("icon-slot"),

    /** Opens the effect catalog. Placed on the homes grid, which is the only screen it belongs on. */
    EFFECTS("effects"),

    /**
     * Opens the animation category. Effect picker only.
     *
     * <p>A category button rather than a generic "open a list" one, because the two categories are a
     * fixed pair rather than data: a third category would be a code change, so letting an operator
     * declare one in menu.yml would promise something the renderer cannot deliver.
     */
    EFFECT_ANIMATIONS("effect-animations"),

    /** Opens the particle category. Effect picker only. */
    EFFECT_PARTICLES("effect-particles"),

    /** Takes off whatever is equipped, back to a clean teleport. Effect picker and its lists. */
    EFFECT_NONE("effect-none"),

    /** Returns from the effect picker to the homes grid, or from a list to the picker. */
    EFFECT_BACK("effect-back"),

    /** Where one category's entries go, the effect list's equivalent of {@link #HOME_SLOT}. */
    EFFECT_SLOT("effect-slot");

    private final String key;

    MenuElement(String key) {
        this.key = key;
    }

    /** The name this element carries in menu.yml. */
    public String key() {
        return key;
    }

    /** Resolves a menu.yml element name, or null when nothing matches. */
    public static MenuElement byKey(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        for (MenuElement element : values()) {
            if (element.key.equals(normalized)) {
                return element;
            }
        }
        return null;
    }
}
