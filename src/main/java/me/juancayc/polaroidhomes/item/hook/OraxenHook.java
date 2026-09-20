package me.juancayc.polaroidhomes.item.hook;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** {@code oraxen:my_item} */
public final class OraxenHook implements ItemHook {

    @Override
    public String getPrefix() {
        return "oraxen";
    }

    @Override
    public boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("Oraxen");
    }

    @Override
    public @Nullable ItemStack getItem(String id) {
        ItemBuilder builder = OraxenItems.getItemById(id);
        return builder != null ? builder.build() : null;
    }

    @Override
    public @Nullable String getId(ItemStack item) {
        return OraxenItems.getIdByItem(item);
    }
}
