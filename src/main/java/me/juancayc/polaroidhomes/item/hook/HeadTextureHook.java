package me.juancayc.polaroidhomes.item.hook;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** {@code texture:<hash>} — a head built from a textures.minecraft.net hash. No dependency. */
public final class HeadTextureHook implements ItemHook {

    @Override
    public String getPrefix() {
        return "texture";
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
        return SkullUtil.fromTexture(id);
    }
}
