package me.juancayc.polaroidhomes.menu;

import me.juancayc.polaroidhomes.item.ItemManager;
import me.juancayc.polaroidhomes.text.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Builds a menu button from a message key and an item reference.
 *
 * <p>This is the single render choke point, so it is also where the persistent-data marker goes.
 * Marking anywhere else means a new button added later will be unmarked, and an unmarked button
 * that escapes the window is indistinguishable from a real item.
 */
public final class MenuItems {

    private final ItemManager items;
    private final MessageService messages;
    private final MenuItemMarker marker;
    private final Logger logger;

    /**
     * References already reported as unresolvable.
     *
     * <p>Lives on this object rather than statically, so a reload gets one fresh report per broken
     * reference: that is the moment an operator is actually watching the console.
     */
    private final Set<String> warnedReferences = ConcurrentHashMap.newKeySet();

    public MenuItems(ItemManager items, MessageService messages, MenuItemMarker marker,
                     Logger logger) {
        this.items = items;
        this.messages = messages;
        this.marker = marker;
        this.logger = logger;
    }

    /**
     * Builds a button.
     *
     * @param reference item reference, resolved through the item layer
     * @param nameKey   message key for the display name, or null for no name
     * @param loreKey   message key for the lore list, or null for no lore
     * @param fallback  material used when the reference resolves to nothing
     */
    public ItemStack button(String reference,
                            @Nullable String nameKey,
                            @Nullable String loreKey,
                            Material fallback,
                            String... replacements) {
        ItemStack item = resolveOrFallback(reference, fallback);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (nameKey != null) {
                meta.displayName(messages.item(nameKey, replacements));
            }
            if (loreKey != null) {
                List<String> raw = messages.rawList(loreKey);
                if (!raw.isEmpty()) {
                    List<Component> lore = new ArrayList<>(raw.size());
                    for (String line : raw) {
                        lore.add(messages.itemLine(line, replacements));
                    }
                    meta.lore(lore);
                }
            }
            item.setItemMeta(meta);
        }
        return marker.mark(item);
    }

    /**
     * Builds a button from a template element declaration.
     *
     * <p>The declaration's name and lore win when it sets them, and the message keys are used when
     * it does not. That is what lets an operator restyle one button in menu.yml without having to
     * copy the whole language file, and what lets the shipped default keep taking its text from the
     * translation the server already has.
     *
     * @param definition  the element's declaration, or null when the template declared none
     * @param itemDefault item reference used when the declaration names no item
     */
    public ItemStack element(@Nullable MenuTemplate.ElementDefinition definition,
                             String itemDefault,
                             @Nullable String nameKey,
                             @Nullable String loreKey,
                             Material fallback,
                             String... replacements) {
        String reference = definition != null && !definition.item().isBlank()
                ? definition.item()
                : itemDefault;

        ItemStack item = resolveOrFallback(reference, fallback);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyName(meta, definition, nameKey, replacements);
            applyLore(meta, definition, loreKey, replacements);
            if (definition != null) {
                if (definition.modelData() != null) {
                    applyModelData(meta, definition.modelData());
                }
                if (definition.glow()) {
                    // Enchantment-glint override rather than a real enchantment: a fake enchantment
                    // shows up in the tooltip and in anvil behaviour, a glint override does not.
                    meta.setEnchantmentGlintOverride(true);
                }
            }
            item.setItemMeta(meta);
        }
        return marker.mark(item);
    }

    /**
     * Sets custom model data.
     *
     * <p>The single-int form is deprecated in favour of the component one, and is used anyway: it is
     * what every resource pack in the wild keys its overrides off, and the component replacement is
     * still marked experimental in Paper. Isolated in its own method so the suppression covers this
     * one call rather than the whole renderer.
     */
    @SuppressWarnings("deprecation")
    private static void applyModelData(ItemMeta meta, int modelData) {
        meta.setCustomModelData(modelData);
    }

    private void applyName(ItemMeta meta,
                           @Nullable MenuTemplate.ElementDefinition definition,
                           @Nullable String nameKey,
                           String... replacements) {
        if (definition != null && definition.name() != null) {
            meta.displayName(messages.itemLine(definition.name(), replacements));
        } else if (nameKey != null) {
            meta.displayName(messages.item(nameKey, replacements));
        }
    }

    private void applyLore(ItemMeta meta,
                           @Nullable MenuTemplate.ElementDefinition definition,
                           @Nullable String loreKey,
                           String... replacements) {
        List<String> raw = definition != null && !definition.lore().isEmpty()
                ? definition.lore()
                : loreKey == null ? List.of() : messages.rawList(loreKey);
        if (raw.isEmpty()) {
            return;
        }
        List<Component> lore = new ArrayList<>(raw.size());
        for (String line : raw) {
            lore.add(messages.itemLine(line, replacements));
        }
        meta.lore(lore);
    }

    /**
     * Resolves a reference, logging an unresolvable one once and then rendering the safe default.
     *
     * <p>Once per reference on purpose: a reference that cannot resolve cannot resolve on the next
     * render either, and a menu redraws on every page flip, so logging per attempt would fill a
     * console with the same line. The render itself never fails, because a visible wrong item tells
     * an operator more than a window that refuses to open.
     */
    private ItemStack resolveOrFallback(String reference, Material fallback) {
        ItemStack resolved = items.resolve(reference);
        if (resolved != null) {
            return resolved;
        }
        if (warnedReferences.add(reference)) {
            logger.warning("The item reference '" + reference
                    + "' could not be resolved (its plugin may be missing or the id may not exist). "
                    + "Using " + fallback + " instead.");
        }
        return new ItemStack(fallback);
    }

    /**
     * The filler pane.
     *
     * <p>The tooltip is hidden rather than blanked: an empty tooltip box trailing the cursor reads
     * as a broken item, while no tooltip reads as background.
     */
    public ItemStack filler(String reference) {
        ItemStack item = resolveOrFallback(reference, Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return marker.mark(item);
    }
}
