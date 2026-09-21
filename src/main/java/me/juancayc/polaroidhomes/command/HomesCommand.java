package me.juancayc.polaroidhomes.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.juancayc.polaroidhomes.PolaroidHomesPlugin;
import me.juancayc.polaroidhomes.text.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /homes}, {@code /homes reload}, {@code /homes <player>}.
 *
 * <p>A {@link BasicCommand}, not a {@code CommandExecutor}: this is a Paper plugin, and Paper
 * plugins cannot declare commands in {@code paper-plugin.yml} at all. Calling
 * {@code JavaPlugin#getCommand} from one throws {@code UnsupportedOperationException} on enable,
 * so registration goes through the COMMANDS lifecycle event instead — see
 * {@code PolaroidHomesPlugin#registerCommand()}.
 */
public final class HomesCommand implements BasicCommand {

    private final PolaroidHomesPlugin plugin;

    public HomesCommand(PolaroidHomesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        // getExecutor() is the entity that ran it; getSender() is who it is attributed to. For a
        // player-facing menu command the executor is the one whose screen must open.
        CommandSender sender = source.getExecutor() != null ? source.getExecutor() : source.getSender();
        MessageService messages = plugin.messages();

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("polaroidhomes.admin")) {
                messages.send(sender, "general.no_permission");
                return;
            }
            plugin.reloadEverything();
            // Re-read after the reload, because the message service itself was rebuilt.
            plugin.messages().send(sender, "general.reloaded");
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("polaroidhomes.admin")) {
                messages.send(sender, "general.no_permission");
                return;
            }
            // Temporary diagnostic: prints exactly what the bridge reads back from EssentialsX,
            // so a menu that renders no homes can be told apart from a bridge that reads none.
            if (!(sender instanceof Player self)) {
                messages.send(sender, "general.players_only");
                return;
            }
            var bridge = plugin.essentials();
            java.util.List<String> read = bridge.homes(self);
            sender.sendMessage("[PolaroidHomes] essentials available: " + bridge.isAvailable());
            sender.sendMessage("[PolaroidHomes] getHomes() -> size=" + read.size() + " " + read);
            sender.sendMessage("[PolaroidHomes] homeLimit() -> " + bridge.homeLimit(self));
            sender.sendMessage("[PolaroidHomes] tiers() -> " + bridge.tiers());
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players_only");
            return;
        }
        if (!player.hasPermission("polaroidhomes.use")) {
            messages.send(player, "general.no_permission");
            return;
        }
        if (!plugin.essentials().isAvailable()) {
            messages.send(player, "general.essentials_missing");
            return;
        }

        OfflinePlayer target = player;
        if (args.length > 0) {
            if (!player.hasPermission("polaroidhomes.admin")) {
                messages.send(player, "general.no_permission");
                return;
            }
            // Only an online player is accepted: home limits resolve through Bukkit permissions,
            // which are not reliably available for somebody who is not connected, and a menu built
            // on a guessed limit would show the wrong locked slots.
            Player other = Bukkit.getPlayerExact(args[0]);
            if (other == null) {
                messages.send(player, "general.player_not_found",
                        "%player%", MessageService.escape(args[0]));
                return;
            }
            target = other;
        }

        plugin.openHomesMenu(player, target);
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

    /** Gates the whole command, so it never appears in a client's tab list without permission. */
    @Override
    public @Nullable String permission() {
        return "polaroidhomes.use";
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source,
                                               @NotNull String[] args) {
        CommandSender sender = source.getExecutor() != null ? source.getExecutor() : source.getSender();
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
            if ("debug".startsWith(prefix)) {
                suggestions.add("debug");
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
