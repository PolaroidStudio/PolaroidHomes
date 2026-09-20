package me.juancayc.polaroidhomes.item.hook;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** {@code itemsadder:suite:my_item} — the id keeps any further separator after the prefix. */
public final class ItemsAdderHook implements ItemHook {

    @Override
    public String getPrefix() {
        return "itemsadder";
    }

    @Override
    public boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    @Override
    public @Nullable ItemStack getItem(String id) {
        CustomStack stack = CustomStack.getInstance(id);
        return stack != null ? stack.getItemStack() : null;
    }

    @Override
    public @Nullable String getId(ItemStack item) {
        CustomStack stack = CustomStack.byItemStack(item);
        return stack != null ? stack.getId() : null;
    }
}
