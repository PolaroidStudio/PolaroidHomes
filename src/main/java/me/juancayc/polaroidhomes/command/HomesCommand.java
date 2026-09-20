package me.juancayc.polaroidhomes.command;

import me.juancayc.polaroidhomes.PolaroidHomesPlugin;
import me.juancayc.polaroidhomes.text.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /homes}, {@code /homes reload}, {@code /homes <player>}. */
public final class HomesCommand implements CommandExecutor, TabCompleter {

    private final PolaroidHomesPlugin plugin;

    public HomesCommand(PolaroidHomesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageService messages = plugin.messages();

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("polaroidhomes.admin")) {
                messages.send(sender, "general.no_permission");
                return true;
            }
            plugin.reloadEverything();
            // Re-read after the reload, because the message service itself was rebuilt.
            plugin.messages().send(sender, "general.reloaded");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players_only");
            return true;
        }
        if (!player.hasPermission("polaroidhomes.use")) {
            messages.send(player, "general.no_permission");
            return true;
        }
        if (!plugin.essentials().isAvailable()) {
            messages.send(player, "general.essentials_missing");
            return true;
        }

        OfflinePlayer target = player;
        if (args.length > 0) {
            if (!player.hasPermission("polaroidhomes.admin")) {
                messages.send(player, "general.no_permission");
                return true;
            }
            // Only an online player is accepted: home limits resolve through Bukkit permissions,
            // which are not reliably available for somebody who is not connected, and a menu built
            // on a guessed limit would show the wrong locked slots.
            Player other = Bukkit.getPlayerExact(args[0]);
            if (other == null) {
                messages.send(player, "general.player_not_found",
                        "%player%", MessageService.escape(args[0]));
                return true;
            }
            target = other;
        }

        plugin.openHomesMenu(player, target);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageService messages = plugin.messages();
        // Rows inside a block carry no prefix: repeating the banner on every line of one help
        // block is the fastest way to make a plugin feel cheap.
        messages.sendRaw(sender, "command.header");
        messages.sendRaw(sender, "command.help_open");
        if (sender.hasPermission("polaroidhomes.admin")) {
            messages.sendRaw(sender, "command.help_open_other");
            messages.sendRaw(sender, "command.help_reload");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        if ("help".startsWith(prefix)) {
            suggestions.add("help");
        }
        if (sender.hasPermission("polaroidhomes.admin")) {
            if ("reload".startsWith(prefix)) {
                suggestions.add("reload");
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(online.getName());
                }
            }
        }
        return suggestions;
    }
}
