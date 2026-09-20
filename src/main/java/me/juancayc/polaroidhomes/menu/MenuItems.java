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

    public MenuItems(ItemManager items, MessageService messages, MenuItemMarker marker) {
        this.items = items;
        this.messages = messages;
        this.marker = marker;
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
        ItemStack item = items.resolve(reference);
        if (item == null) {
            item = new ItemStack(fallback);
        }
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
     * The filler pane.
     *
     * <p>The tooltip is hidden rather than blanked: an empty tooltip box trailing the cursor reads
     * as a broken item, while no tooltip reads as background.
     */
    public ItemStack filler(String reference) {
        ItemStack item = items.resolve(reference);
        if (item == null) {
            item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return marker.mark(item);
    }
}
