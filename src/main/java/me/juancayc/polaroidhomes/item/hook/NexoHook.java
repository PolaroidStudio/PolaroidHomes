package me.juancayc.polaroidhomes.item.hook;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** {@code nexo:my_item} */
public final class NexoHook implements ItemHook {

    @Override
    public String getPrefix() {
        return "nexo";
    }

    @Override
    public boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("Nexo");
    }

    @Override
    public @Nullable ItemStack getItem(String id) {
        ItemBuilder builder = NexoItems.itemFromId(id);
        return builder != null ? builder.build() : null;
    }

    @Override
    public @Nullable String getId(ItemStack item) {
        return NexoItems.idFromItem(item);
    }
}
