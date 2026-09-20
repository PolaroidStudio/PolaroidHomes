package me.juancayc.polaroidhomes.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** What one menu slot does when clicked. Always dispatched on the tick after the click. */
@FunctionalInterface
public interface SlotHandler {

    void onClick(Player player, ClickType click);
}
