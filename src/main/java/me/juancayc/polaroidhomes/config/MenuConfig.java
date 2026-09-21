package me.juancayc.polaroidhomes.config;

import me.juancayc.polaroidhomes.menu.DefaultMenuTemplates;
import me.juancayc.polaroidhomes.menu.MenuElement;
import me.juancayc.polaroidhomes.menu.MenuTemplate;
import me.juancayc.polaroidhomes.menu.MenuTemplateReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.logging.Logger;

/**
 * Typed view over menu.yml, re-read on every reload.
 *
 * <p>A layout an operator can edit is a layout an operator can break, so the contract of this class
 * is that it always hands the renderer a usable template. A section that does not parse is logged
 * with the offending key and replaced by the shipped default; the plugin is never disabled over a
 * menu typo, because homes and teleports work perfectly well with the default grid.
 *
 * <p>{@code max-displayed-slots} lives here rather than in config.yml: the cap and the layout are
 * one concern — the cap is only meaningful relative to how many content slots the template gives —
 * and splitting them across two files means an operator can only understand either by reading both.
 * {@code ConfigMigrations} moves an existing value across.
 */
public final class MenuConfig {

    private final Plugin plugin;

    private MenuTemplate homes;
    private MenuTemplate icons;
    private MenuTemplate effectPicker;
    private MenuTemplate effectList;
    private int maxDisplayedSlots;

    public MenuConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "menu.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Read before the templates: the homes template's pagination rule is validated against it,
        // so a cap the operator raised past their layout is caught here rather than at render.
        this.maxDisplayedSlots = config.getInt("max-displayed-slots", 45);

        this.homes = load(config, "homes", maxDisplayedSlots,
                MenuElement.HOME_SLOT, DefaultMenuTemplates.HOMES_ELEMENTS,
                DefaultMenuTemplates.homes());
        // The picker's content count is whatever the operator's icon-choices list happens to be, and
        // a shorter list is not a layout mistake. Its paging is therefore validated as "may page",
        // and the renderer simply draws the buttons only when there is a page to go to.
        this.icons = load(config, "icons", 1,
                MenuElement.ICON_SLOT, DefaultMenuTemplates.ICON_ELEMENTS,
                DefaultMenuTemplates.icons());
        // The picker has no content slots at all, so it can never need paging.
        this.effectPicker = load(config, "effects", 1,
                MenuElement.EFFECT_ANIMATIONS, DefaultMenuTemplates.EFFECT_PICKER_ELEMENTS,
                DefaultMenuTemplates.effectPicker());
        // Validated as "may page", like the icon picker: how many entries a category holds is
        // whatever the operator declared, and a catalog shorter than one page is not a mistake.
        this.effectList = load(config, "effect-list", 1,
                MenuElement.EFFECT_SLOT, DefaultMenuTemplates.EFFECT_LIST_ELEMENTS,
                DefaultMenuTemplates.effectList());
    }

    private MenuTemplate load(YamlConfiguration config,
                              String section,
                              int maxTotalSlots,
                              MenuElement gridElement,
                              java.util.Set<MenuElement> allowed,
                              MenuTemplate fallback) {
        MenuTemplate.Result result = MenuTemplateReader.read(
                config.getConfigurationSection(section), maxTotalSlots, gridElement, allowed);
        return select(result, section, fallback, plugin.getLogger());
    }

    /**
     * Turns a parse result into the template the renderer will use, reporting what went wrong.
     *
     * <p>Static and logger-injected so the guarantee this class actually makes — a broken section
     * produces the shipped layout, not an exception and not a half-built window — is provable in a
     * headless test rather than only on a server with a deliberately broken file.
     */
    public static MenuTemplate select(MenuTemplate.Result result,
                                      String section,
                                      MenuTemplate fallback,
                                      Logger logger) {
        for (String warning : result.warnings()) {
            logger.warning("menu.yml '" + section + "': " + warning);
        }
        if (result.isValid()) {
            return result.template();
        }
        for (String error : result.errors()) {
            logger.severe("menu.yml '" + section + "': " + error);
        }
        logger.severe("menu.yml '" + section
                + "' was not usable, so the built-in layout is being used instead. "
                + "Fix the errors above and run /homes reload.");
        return fallback;
    }

    /** The homes grid layout. Never null, and never an invalid one. */
    public MenuTemplate homes() {
        return homes;
    }

    /** The icon picker layout. Never null, and never an invalid one. */
    public MenuTemplate icons() {
        return icons;
    }

    /** The effect category picker layout. Never null, and never an invalid one. */
    public MenuTemplate effectPicker() {
        return effectPicker;
    }

    /**
     * The layout one category's effect list uses. Never null, and never an invalid one.
     *
     * <p>One layout for both categories rather than one each. The two lists differ only in which
     * entries they draw, so two layouts would be two things to keep in step for no gain, and an
     * operator who restyled one and not the other would ship a menu that changes shape halfway
     * through.
     */
    public MenuTemplate effectList() {
        return effectList;
    }

    /** Ceiling on home slots drawn across all pages of the homes grid. */
    public int maxDisplayedSlots() {
        return maxDisplayedSlots;
    }
}
