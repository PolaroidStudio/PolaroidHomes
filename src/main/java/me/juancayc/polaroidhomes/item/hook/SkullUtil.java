package me.juancayc.polaroidhomes.item.hook;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Vanilla player heads through Paper's PlayerProfile API, with no head-plugin dependency. */
public final class SkullUtil {

    private SkullUtil() {
    }

    /** Head from a base64 "textures" property value. */
    public static ItemStack fromBase64(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (!(head.getItemMeta() instanceof SkullMeta meta)) {
            return head;
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    /** Head from a texture hash: the part after textures.minecraft.net/texture/. */
    public static ItemStack fromTexture(String texture) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + texture + "\"}}}";
        return fromBase64(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }

    /** Head carrying a named player's skin. */
    public static ItemStack fromName(String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (!(head.getItemMeta() instanceof SkullMeta meta)) {
            return head;
        }
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
        head.setItemMeta(meta);
        return head;
    }
}
