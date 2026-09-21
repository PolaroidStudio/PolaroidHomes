package me.juancayc.polaroidhomes.menu;

import me.juancayc.polaroidhomes.config.MenuConfig;
import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.edit.DeleteConfirmations;
import me.juancayc.polaroidhomes.edit.RenamePrompts;
import me.juancayc.polaroidhomes.effect.EffectPlayer;
import me.juancayc.polaroidhomes.effect.catalog.SqlEffectStorage;
import me.juancayc.polaroidhomes.provider.HomeProvider;
import me.juancayc.polaroidhomes.icon.IconStorage;
import me.juancayc.polaroidhomes.item.ItemManager;
import me.juancayc.polaroidhomes.text.MessageService;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The services every menu needs, passed as one value.
 *
 * <p>Handed around rather than reached for through a plugin singleton, so a menu can be built
 * against a reloaded configuration while an older window is still open with the previous one.
 */
public record MenuContext(Plugin plugin,
                          PluginConfig config,
                          MenuConfig menus,
                          MessageService messages,
                          ItemManager itemManager,
                          MenuItems items,
                          MenuRegistry registry,
                          HomeProvider provider,
                          IconStorage icons,
                          DeleteConfirmations deletes,
                          RenamePrompts renames,
                          SqlEffectStorage effects,
                          EffectPlayer effectPlayer) {

    /**
     * True when an item reference produces something the server can actually render.
     *
     * <p>Used to drop a configured icon choice whose plugin is absent or whose id was deleted,
     * before it reaches the picker. A picker slot that resolves to nothing is a slot a player can
     * pick and then find their home has no picture.
     */
    public boolean resolves(String reference) {
        return itemManager.resolve(reference) != null;
    }

    /**
     * The one click sound for the whole plugin.
     *
     * <p>Played only by buttons that actually do something: a sound on every click, including the
     * ones that go nowhere, turns a menu into a slot machine.
     */
    public void playClick(Player player) {
        Sound sound = config.clickSound();
        if (sound != null) {
            player.playSound(player.getLocation(), sound, config.clickVolume(), config.clickPitch());
        }
    }
}
