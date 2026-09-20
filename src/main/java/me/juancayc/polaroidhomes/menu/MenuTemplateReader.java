package me.juancayc.polaroidhomes.menu;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns one {@code menu.yml} section into a {@link MenuTemplate}.
 *
 * <p>Split from {@link MenuTemplate} so the rules stay Bukkit-free and unit-testable while the
 * YAML shape lives with the only class that needs to know it. This class does no validation of its
 * own beyond what YAML itself enforces: everything that can be wrong about a layout is decided by
 * {@link MenuTemplate#parse}, in one place, with one set of messages.
 */
public final class MenuTemplateReader {

    private MenuTemplateReader() {
    }

    /**
     * Reads a menu section.
     *
     * @param section       the {@code homes} or {@code icons} section of menu.yml, possibly null
     * @param maxTotalSlots how many content slots the menu must show across all pages
     * @param gridElement   the element that marks a content slot for this menu
     * @param allowed       the elements this menu renders
     */
    public static MenuTemplate.Result read(@Nullable ConfigurationSection section,
                                           int maxTotalSlots,
                                           MenuElement gridElement,
                                           Set<MenuElement> allowed) {
        if (section == null) {
            return new MenuTemplate.Result(null,
                    List.of("the section is missing from menu.yml."), List.of());
        }

        List<String> rows = section.getStringList("rows");
        ConfigurationSection elements = section.getConfigurationSection("elements");

        Map<Character, MenuElement> types = new LinkedHashMap<>();
        Map<Character, MenuTemplate.ElementDefinition> definitions = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        if (elements != null) {
            for (String key : elements.getKeys(false)) {
                if (key.length() != 1) {
                    errors.add("'elements." + key + "' is not a single character. Each element is "
                            + "keyed by the one character that places it in a row.");
                    continue;
                }
                char symbol = key.charAt(0);
                if (symbol == MenuTemplate.EMPTY) {
                    errors.add("'elements' declares a space. A space always means an empty slot "
                            + "and cannot be redefined.");
                    continue;
                }
                ConfigurationSection element = elements.getConfigurationSection(key);
                if (element == null) {
                    errors.add("'elements." + key + "' is not a section. It needs at least a "
                            + "'type'.");
                    continue;
                }
                MenuElement type = MenuElement.byKey(element.getString("type"));
                if (type == null) {
                    errors.add("'elements." + key + ".type' is '" + element.getString("type")
                            + "', which is not a known element.");
                    continue;
                }
                types.put(symbol, type);
                definitions.put(symbol, definition(element));
            }
        }

        MenuTemplate.Result result = MenuTemplate.parse(
                rows, definitions, types, maxTotalSlots, gridElement, allowed);

        if (errors.isEmpty()) {
            return result;
        }
        // Element-level problems are reported together with any layout ones, so an operator fixes
        // the file once instead of discovering the next error on the next restart.
        List<String> combined = new ArrayList<>(errors);
        combined.addAll(result.errors());
        return new MenuTemplate.Result(null, combined, result.warnings());
    }

    private static MenuTemplate.ElementDefinition definition(ConfigurationSection element) {
        String item = element.getString("item", "");
        String name = element.getString("name", null);
        List<String> lore = element.getStringList("lore");
        Integer modelData = element.contains("custom-model-data")
                ? element.getInt("custom-model-data")
                : null;
        boolean glow = element.getBoolean("glow", false);
        return new MenuTemplate.ElementDefinition(item, name, List.copyOf(lore), modelData, glow);
    }
}
