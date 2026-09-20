package me.juancayc.polaroidhomes.item.hook;

import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** {@code headdatabase:12345} */
public final class HeadDatabaseHook implements ItemHook {

    // Cached: the icon picker resolves every configured choice on each open, and building the API
    // object per lookup would repeat that work for nothing.
    private HeadDatabaseAPI api;

    @Override
    public String getPrefix() {
        return "headdatabase";
    }

    @Override
    public boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("HeadDatabase");
    }

    @Override
    public @Nullable ItemStack getItem(String id) {
        try {
            if (api == null) {
                api = new HeadDatabaseAPI();
            }
            return api.getItemHead(id);
        } catch (Exception ignored) {
            // HeadDatabase throws on an unknown id. A hook must answer null and never throw, or a
            // single mistyped config entry takes the whole menu render down with it.
            return null;
        }
    }

    @Override
    public @Nullable String getId(ItemStack item) {
        try {
            if (api == null) {
                api = new HeadDatabaseAPI();
            }
            return api.getItemID(item);
        } catch (Exception ignored) {
            return null;
        }
    }
}
