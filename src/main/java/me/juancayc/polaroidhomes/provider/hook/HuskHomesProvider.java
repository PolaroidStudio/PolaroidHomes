package me.juancayc.polaroidhomes.provider.hook;

import me.juancayc.polaroidhomes.provider.HomeProvider;
import me.juancayc.polaroidhomes.provider.HomeSnapshot;
import me.juancayc.polaroidhomes.provider.HomeTier;
import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.position.Home;
import net.william278.huskhomes.user.OnlineUser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads homes from HuskHomes.
 *
 * <p>HuskHomes stores homes in its own database, local or cross-server, and hands them back through
 * {@code CompletableFuture}. Its documentation is explicit that {@code join()} must never be called
 * on those futures: they complete on HuskHomes' own executor, so blocking the server thread on one
 * deadlocks the server. Nothing in this class blocks — {@link #snapshot} returns the future the API
 * gave it, mapped into the shape the menu wants, and the caller opens its window in the callback.
 *
 * <p>The API is resolved on every call rather than cached, because {@code HuskHomesAPI.getInstance()}
 * throws until HuskHomes has registered it. Holding a reference obtained once at enable would break
 * on any reload that re-registers the API.
 */
public final class HuskHomesProvider implements HomeProvider {

    private final Logger logger;

    public HuskHomesProvider(Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    @Override
    public String id() {
        return "huskhomes";
    }

    @Override
    public String pluginName() {
        return "HuskHomes";
    }

    /**
     * Resolves the API, or null when HuskHomes is absent, disabled, or has not registered yet.
     *
     * <p>{@code getInstance()} throws {@code NotRegisteredException} rather than returning null, and
     * that is a state rather than an error here: the plugin may simply not be installed. Catching
     * broadly is deliberate — a linkage error from a HuskHomes version whose API moved must also read
     * as "unavailable" rather than take the server down with it.
     */
    private @Nullable HuskHomesAPI api() {
        Plugin husk = Bukkit.getPluginManager().getPlugin("HuskHomes");
        if (husk == null || !husk.isEnabled()) {
            return null;
        }
        try {
            return HuskHomesAPI.getInstance();
        } catch (Throwable ex) {
            logger.log(Level.FINE, "HuskHomes is enabled but its API is not registered yet.", ex);
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return api() != null;
    }

    /**
     * False: HuskHomes has no enumerable ranks.
     *
     * <p>It resolves a player's allowance from numeric {@code huskhomes.max_homes.<n>} permissions
     * and keeps no list of the groups that grant them, so there is nothing to read and no honest way
     * to name "the rank that unlocks slot 7". The menu shows such a slot as locked without naming a
     * rank rather than inventing one.
     */
    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public CompletableFuture<HomeSnapshot> snapshot(Player viewer, OfflinePlayer target) {
        HuskHomesAPI api = api();
        Player online = target.getPlayer();
        if (api == null || online == null) {
            // An offline target has no OnlineUser, and the limit is permission-derived, so it cannot
            // be resolved for somebody who is not connected. An empty grid is the honest answer.
            return CompletableFuture.completedFuture(HomeSnapshot.empty());
        }

        OnlineUser user = api.adaptUser(online);
        int limit = api.getMaxHomeSlots(user);
        return api.getUserHomes(user)
                .thenApply(homes -> toSnapshot(api, homes, limit))
                // Never propagated: a database timeout inside HuskHomes must not leave the command
                // with a future nobody completes, which would look to the player like the menu simply
                // never opened.
                .exceptionally(ex -> {
                    logger.log(Level.WARNING, "HuskHomes could not read the homes of "
                            + target.getUniqueId() + "; showing an empty grid.", ex);
                    return HomeSnapshot.empty();
                });
    }

    private HomeSnapshot toSnapshot(HuskHomesAPI api, List<Home> homes, int limit) {
        List<String> names = new ArrayList<>(homes.size());
        Map<String, Location> locations = new HashMap<>();
        for (Home home : homes) {
            String name = home.getMeta().getName();
            if (name == null) {
                continue;
            }
            names.add(name);
            Location location = location(api, home);
            if (location != null) {
                locations.put(name, location);
            }
        }
        // No tiers: see supportsTiers. The snapshot's own highestLimit() then falls back to this
        // player's limit, which sizes the grid to exactly what they are allowed.
        return new HomeSnapshot(names, limit, List.of(), locations);
    }

    /**
     * Converts a HuskHomes position to a Bukkit location.
     *
     * <p>Returns null for a home on another server in a proxied network, or in a world this server
     * has not loaded: {@code getLocation} cannot resolve a world that is not here. The menu then
     * draws the home without coordinates rather than omitting it, because the home is real and
     * teleporting to it still works — HuskHomes handles the cross-server hop itself.
     */
    private @Nullable Location location(HuskHomesAPI api, Home home) {
        try {
            return api.getLocation(home);
        } catch (Throwable ex) {
            logger.log(Level.FINE, "Could not resolve a Bukkit location for home "
                    + home.getMeta().getName(), ex);
            return null;
        }
    }

    /**
     * Teleports through HuskHomes' own timed-teleport path.
     *
     * <p>{@code toTimedTeleport()} is what applies HuskHomes' warmup, its cooldown, its economy hooks
     * and its {@code TeleportWarmupEvent}/{@code TeleportEvent} chain — the same reason the EssentialsX
     * provider dispatches a command instead of moving the player. The home is looked up from the
     * player's own list, so a name that is not theirs cannot be used to reach somebody else's home.
     */
    @Override
    public boolean teleport(Player player, String home) {
        HuskHomesAPI api = api();
        if (api == null) {
            return false;
        }
        OnlineUser user = api.adaptUser(player);
        // getHome is a future as well, so the teleport is built in its callback. Returning true here
        // means "accepted", not "arrived"; a home that turns out not to exist reports itself through
        // HuskHomes' own message, which is the one the player already recognises.
        api.getHome(user, home).thenAccept(found -> found.ifPresent(target -> {
            try {
                api.teleportBuilder()
                        .teleporter(user)
                        .target(target)
                        .toTimedTeleport()
                        .execute();
                // TeleportationException extends IllegalStateException, and toTimedTeleport() throws
                // the bare one when the builder is incomplete, so one catch covers both.
            } catch (IllegalStateException ex) {
                logger.log(Level.WARNING, "HuskHomes refused a teleport to home " + home, ex);
            }
        }));
        return true;
    }
}
