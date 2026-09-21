package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.PolaroidHomesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Turns a typed {@code /homes} into a menu open, without registering the label.
 *
 * <p>Registering it is not available: both backends already own {@code homes} - EssentialsX as an
 * alias of {@code /home}, HuskHomes as an alias of {@code /homelist} - and both load first, so the
 * registration would lose the race with nothing logged anywhere. That was a real shipped bug, and
 * {@code CommandLabelTest} pins the registered labels against it. This listener is the other half:
 * the label stays with its owner and the typed line is caught before dispatch.
 *
 * <p>Registered at HIGHEST rather than LOWEST on purpose. A permission plugin, an anti-spam filter
 * or a command-blocker should get to cancel a line before this plugin repurposes it; taking the
 * line first would make this plugin an unintended bypass for every one of them.
 * {@code ignoreCancelled} then keeps a line somebody else refused from opening a menu.
 *
 * <p>The matching rule itself lives in {@code CommandInterceptor}, Bukkit-free and unit-tested,
 * because the rule is where the subtlety is: only a bare command is ever swallowed, so
 * {@code /homes someplayer} and {@code /home base} still reach their owners.
 */
public final class CommandInterceptListener implements Listener {

    private final PolaroidHomesPlugin plugin;

    public CommandInterceptListener(PolaroidHomesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // Read from the live config rather than cached in a field: a reload rebuilds the config
        // object, and a listener holding the old interceptor would keep swallowing a label the
        // operator has just turned off.
        if (!plugin.config().interceptor().intercepts(event.getMessage())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("polaroidhomes.use")) {
            // Not intercepted at all rather than refused: a player without permission for this
            // menu still has whatever permission the backend's own command needs, and swallowing
            // their /homes to tell them they may not use a menu they did not ask for would break
            // a command that works.
            return;
        }
        if (!plugin.provider().isAvailable()) {
            // Same reasoning: with no backend answering there is no menu to show, so the line is
            // left to whoever else wants it.
            return;
        }

        event.setCancelled(true);
        plugin.openHomesMenu(player, player);
    }
}
