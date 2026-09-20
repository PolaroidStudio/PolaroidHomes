package me.juancayc.polaroidhomes.item.hook;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** {@code basehead:<base64>} — a head from a raw base64 textures value. No dependency. */
public final class HeadBase64Hook implements ItemHook {

    @Override
    public String getPrefix() {
        return "basehead";
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
        return SkullUtil.fromBase64(id);
    }
}
