package me.juancayc.polaroidhomes.provider;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * The home backend this plugin reads from. One implementation per supported homes plugin.
 *
 * <p>This plugin owns icons and effects; it never owns a home or a home limit. Every implementation
 * therefore reads and never writes, with the single exception of {@link #teleport}, which is routed
 * through the backend's own command or API so its warmup, cooldown, charge and event chain stay
 * intact. Moving the player directly would bypass all of that and silence the effects that hang off
 * those events.
 *
 * <p><b>The surface is async because one provider is.</b> HuskHomes answers with
 * {@code CompletableFuture} and forbids blocking on it; EssentialsX answers synchronously. Rather
 * than let the menu learn which one it is talking to, {@link #snapshot} is always a future. A
 * synchronous provider completes it inline — no scheduler hop, no thread — so the async shape costs
 * it nothing.
 */
public interface HomeProvider {

    /** The value {@code hooks.home-provider} accepts for this provider, lowercase. */
    String id();

    /** The backend plugin's own name, for log lines an operator has to act on. */
    String pluginName();

    /**
     * True when the backend is loaded, enabled, and its API is answering right now.
     *
     * <p>Checked rather than assumed even after selection: an operator can disable a plugin at
     * runtime, and a stale reference to a disabled backend is worse than no reference.
     */
    boolean isAvailable();

    /**
     * Whether this provider can enumerate the ranks that grant home limits.
     *
     * <p>False is a legitimate answer, not a degraded one: HuskHomes resolves a limit from numeric
     * {@code huskhomes.max_homes.<n>} permissions and keeps no list of named ranks anywhere. The
     * menu asks this so it can show a locked slot as simply locked instead of naming a rank that
     * does not exist.
     */
    boolean supportsTiers();

    /**
     * Reads everything the menu needs about one player, off the render path.
     *
     * <p>The future completes on whatever thread the backend chose, so the caller is responsible for
     * hopping back to the main thread before touching an inventory. It never completes
     * exceptionally: a backend failure resolves to {@link HomeSnapshot#empty()} and is logged, so an
     * unreachable database shows an empty grid rather than swallowing the command.
     *
     * @param viewer the player whose window this is, needed because permission-derived limits only
     *               resolve for somebody connected
     * @param target the player whose homes are being shown, the viewer themselves in the common case
     */
    CompletableFuture<HomeSnapshot> snapshot(Player viewer, OfflinePlayer target);

    /**
     * Sends the player to one of their own homes through the backend's own teleport path.
     *
     * @return false when the backend refused or is unavailable, which the caller reports to the
     *         player rather than leaving them staring at a closed menu
     */
    boolean teleport(Player player, String home);
}
