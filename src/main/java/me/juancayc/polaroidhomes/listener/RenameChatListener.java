package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.PolaroidHomesPlugin;
import me.juancayc.polaroidhomes.edit.HomeNameRules;
import me.juancayc.polaroidhomes.edit.RenamePrompts;
import me.juancayc.polaroidhomes.text.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;

/**
 * Reads the chat line that answers a rename prompt.
 *
 * <p>Registered at LOWEST so the line is taken before any chat-formatting plugin decorates it, and
 * cancelled so the new name never reaches public chat. A player renaming a home to something
 * embarrassing should not broadcast it to the server.
 *
 * <p>The event is asynchronous, which is the whole reason this class is so careful about what runs
 * where: everything that touches the provider, the icon store or a message is deferred to the main
 * thread, and only the prompt bookkeeping — a map lookup — happens on the chat thread. The provider
 * call in particular is main-thread work on EssentialsX, which reads its user cache directly.
 *
 * <p>{@code AsyncPlayerChatEvent} is deprecated on modern Paper in favour of Paper's own
 * {@code AsyncChatEvent}, and is used anyway: this event is the one every chat plugin in the
 * ecosystem still fires and listens to, and the deprecated Bukkit event remains the interoperable
 * one. The suppression is on the handler alone so it cannot spread.
 */
public final class RenameChatListener implements Listener {

    private final PolaroidHomesPlugin plugin;
    private final RenamePrompts prompts;

    public RenameChatListener(PolaroidHomesPlugin plugin, RenamePrompts prompts) {
        this.plugin = plugin;
        this.prompts = prompts;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (!prompts.hasPending(id, now)) {
            return;
        }
        RenamePrompts.Pending pending = prompts.take(id, now);
        if (pending == null) {
            return;
        }

        // Cancelled whatever the answer turns out to be: the player was answering this plugin, so
        // the line is not chat regardless of whether the name is accepted.
        event.setCancelled(true);
        String typed = event.getMessage();

        plugin.getServer().getScheduler().runTask(plugin, () -> apply(player, pending, typed));
    }

    /**
     * Validates and applies the answer, on the main thread.
     *
     * <p>The player's homes are re-read here rather than carried over from the window's snapshot.
     * The snapshot was taken when the menu opened and the player has since had up to the prompt's
     * whole timeout to create another home from a command, so a duplicate check against the
     * snapshot could accept a name that is already taken.
     */
    private void apply(Player player, RenamePrompts.Pending pending, String typed) {
        if (!player.isOnline()) {
            return;
        }
        String home = pending.home();
        if (RenamePrompts.isCancel(typed)) {
            plugin.messages().send(player, "rename.cancelled",
                    "%home%", MessageService.escape(home));
            return;
        }

        plugin.provider().snapshot(player, player).thenAccept(snapshot -> onMainThread(() -> {
            if (!player.isOnline()) {
                return;
            }
            validateAndRename(player, home, typed, snapshot.homes());
        }));
    }

    private void validateAndRename(Player player, String home, String typed, List<String> existing) {
        HomeNameRules.Result result = HomeNameRules.check(typed, home, existing);
        if (!result.isOk()) {
            plugin.messages().send(player, result.messageKey(),
                    "%home%", MessageService.escape(home),
                    "%new%", MessageService.escape(HomeNameRules.normalize(typed)),
                    "%max%", String.valueOf(HomeNameRules.MAX_LENGTH));
            return;
        }

        String newName = HomeNameRules.normalize(typed);
        if (!plugin.provider().rename(player, home, newName)) {
            plugin.messages().send(player, "rename.failed",
                    "%home%", MessageService.escape(home));
            return;
        }
        // The icon is NOT moved here. Both backends fire their own rename event and this plugin's
        // icon-lifecycle listener is hooked to exactly that event, so moving it here as well would
        // run the move twice - and the second run would read a source key the first has emptied,
        // leaving the icon on neither name.
        plugin.messages().send(player, "rename.done",
                "%home%", MessageService.escape(home),
                "%new%", MessageService.escape(newName));
    }

    /**
     * A player who logs out with a prompt open drops it.
     *
     * <p>Without this the entry survives their next login and their first sentence becomes a rename
     * attempt for a home they were thinking about half an hour ago.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.clear(event.getPlayer().getUniqueId());
    }

    private void onMainThread(Runnable action) {
        if (plugin.getServer().isPrimaryThread()) {
            action.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, action);
        }
    }
}
