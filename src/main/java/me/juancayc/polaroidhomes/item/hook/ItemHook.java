package me.juancayc.polaroidhomes.item.hook;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Contract for resolving an item reference belonging to one plugin or namespace. */
public interface ItemHook {

    /** The reference prefix. References match as prefix + "-" or prefix + ":" followed by the id. */
    String getPrefix();

    /** True when the backing plugin is present; vanilla hooks are always enabled. */
    boolean isEnabled();

    /** Resolves the id (everything after the separator), or null when it is unknown. */
    @Nullable ItemStack getItem(String id);

    /** Reverse lookup: this hook's id for the given item, or null. */
    default @Nullable String getId(ItemStack item) {
        return null;
    }
}
