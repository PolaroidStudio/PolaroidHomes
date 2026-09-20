package me.juancayc.polaroidhomes.item;

import me.juancayc.polaroidhomes.item.hook.HeadBase64Hook;
import me.juancayc.polaroidhomes.item.hook.HeadDatabaseHook;
import me.juancayc.polaroidhomes.item.hook.HeadNameHook;
import me.juancayc.polaroidhomes.item.hook.HeadTextureHook;
import me.juancayc.polaroidhomes.item.hook.ItemHook;
import me.juancayc.polaroidhomes.item.hook.ItemsAdderHook;
import me.juancayc.polaroidhomes.item.hook.NexoHook;
import me.juancayc.polaroidhomes.item.hook.OraxenHook;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Prefix-based item resolver. Turns an item-reference string into an ItemStack, or null. */
public final class ItemManager {

    private final List<ItemHook> hooks = new ArrayList<>();

    /**
     * Registers the bundled hooks.
     *
     * <p>Every hook here is registered unconditionally. Whether its plugin exists is asked at
     * resolve time through {@code isEnabled()}, because a hook registered at enable would be wrong
     * for a plugin that loads later or is disabled by an operator mid-session.
     */
    public static ItemManager withDefaultHooks() {
        ItemManager manager = new ItemManager();
        manager.registerHook(new NexoHook());
        manager.registerHook(new ItemsAdderHook());
        manager.registerHook(new OraxenHook());
        manager.registerHook(new HeadDatabaseHook());
        // Vanilla hooks last: they never fail and so must not shadow a plugin prefix.
        manager.registerHook(new HeadNameHook());
        manager.registerHook(new HeadTextureHook());
        manager.registerHook(new HeadBase64Hook());
        return manager;
    }

    public void registerHook(ItemHook hook) {
        hooks.add(hook);
    }

    /**
     * Resolves {@code prefix:id} or {@code prefix-id} through a hook, or a vanilla material name.
     * Answers null when nothing matches, so a caller can drop the entry instead of rendering a
     * broken slot.
     */
    public @Nullable ItemStack resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        Match match = match(reference);
        if (match != null) {
            if (!match.hook.isEnabled()) {
                return null;
            }
            ItemStack resolved = match.hook.getItem(match.id);
            return isUsable(resolved) ? resolved : null;
        }
        Material material = Material.matchMaterial(reference.toUpperCase(Locale.ROOT));
        return material != null && material != Material.AIR ? new ItemStack(material) : null;
    }

    private static boolean isUsable(@Nullable ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

    private @Nullable Match match(String reference) {
        for (ItemHook hook : hooks) {
            String name = hook.getPrefix();
            // Both separators are accepted; the id keeps any further '-' or ':' it contains, which
            // is what makes a nested reference like itemsadder:suite:item work.
            if (reference.startsWith(name + "-") || reference.startsWith(name + ":")) {
                return new Match(hook, reference.substring(name.length() + 1));
            }
        }
        return null;
    }

    private record Match(ItemHook hook, String id) {
    }
}
