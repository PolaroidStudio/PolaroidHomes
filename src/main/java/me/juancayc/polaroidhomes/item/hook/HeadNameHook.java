package me.juancayc.polaroidhomes.item.hook;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** {@code head:Steve} — a player head carrying that player's skin. No dependency. */
public final class HeadNameHook implements ItemHook {

    @Override
    public String getPrefix() {
        return "head";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public @Nullable ItemStack getItem(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return SkullUtil.fromName(id);
    }
}
