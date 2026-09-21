package me.juancayc.polaroidhomes.provider.hook;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import me.juancayc.polaroidhomes.provider.HomeProvider;
import me.juancayc.polaroidhomes.provider.HomeSnapshot;
import me.juancayc.polaroidhomes.provider.HomeTier;
import net.ess3.api.IUser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads homes from EssentialsX.
 *
 * <p>EssentialsX owns homes and home limits outright: this class reads them and never writes a limit
 * of its own. The limit a player has is whatever {@code Settings.getHomeLimit(User)} resolves, which
 * already walks the {@code sethome-multiple} groups and tests
 * {@code essentials.sethome.multiple.<group>} through Bukkit, taking the highest match. That is why
 * there is no permissions-plugin dependency anywhere in this project: adding one would give two
 * answers to a question that already has one.
 *
 * <p>Every read here is synchronous, so {@link #snapshot} completes inline and pays no scheduler
 * hop. The async signature is there for HuskHomes, which cannot answer that way.
 */
public final class EssentialsHomeProvider implements HomeProvider {

    /** EssentialsX's own last-resort limit when nothing at all is configured. */
    private static final int ESSENTIALS_DEFAULT_LIMIT = 3;

    private final Logger logger;
    private Essentials essentials;

    public EssentialsHomeProvider(Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    @Override
    public String id() {
        return "essentialsx";
    }

    @Override
    public String pluginName() {
        return "EssentialsX";
    }

    /**
     * Resolves the Essentials instance.
     *
     * <p>Looked up lazily rather than cached at construction: the plugin is only a soft dependency
     * now, so it may be enabled after this object exists, and an operator can still disable it at
     * runtime. A stale reference to a disabled plugin is worse than no reference.
     */
    private @Nullable Essentials essentials() {
        if (essentials != null && essentials.isEnabled()) {
            return essentials;
        }
        Plugin found = Bukkit.getPluginManager().getPlugin("Essentials");
        essentials = found instanceof Essentials casted && casted.isEnabled() ? casted : null;
        return essentials;
    }

    @Override
    public boolean isAvailable() {
        return essentials() != null;
    }

    /** EssentialsX enumerates its own ranks, so the menu can name the one that unlocks a slot. */
    @Override
    public boolean supportsTiers() {
        return true;
    }

    @Override
    public CompletableFuture<HomeSnapshot> snapshot(Player viewer, OfflinePlayer target) {
        // Completed, not scheduled: every call below returns on the calling thread, and handing the
        // caller an already-finished future lets the menu open in the same tick.
        return CompletableFuture.completedFuture(read(target));
    }

    private HomeSnapshot read(OfflinePlayer target) {
        Essentials ess = essentials();
        Player online = target.getPlayer();
        if (ess == null || online == null) {
            // An offline target has no resolvable limit: EssentialsX derives it from Bukkit
            // permissions, which are not reliably attached to somebody who is not connected. Showing
            // a guessed limit would draw the wrong locked slots.
            return HomeSnapshot.empty();
        }
        IUser user = ess.getUser(online);
        if (user == null) {
            return HomeSnapshot.empty();
        }

        List<String> homes = List.copyOf(user.getHomes());
        Map<String, Location> locations = new HashMap<>();
        for (String home : homes) {
            Location location = location(user, home);
            if (location != null) {
                locations.put(home, location);
            }
        }
        return new HomeSnapshot(homes, limit(ess, user), tiers(ess), locations);
    }

    private @Nullable Location location(IUser user, String name) {
        try {
            return user.getHome(name);
        } catch (Exception ex) {
            // EssentialsX throws when the home's world is not loaded. That is an ordinary state on a
            // server with a rotating minigame world, not something worth a stack trace.
            logger.log(Level.FINE, "Could not read home " + name, ex);
            return null;
        }
    }

    /**
     * The player's resolved home limit, straight from EssentialsX.
     *
     * <p>This already accounts for every {@code essentials.sethome.multiple.<group>} permission the
     * player holds.
     */
    private int limit(Essentials ess, IUser user) {
        // The concrete User is required rather than the interface: getHomeLimit is only overloaded
        // for User and for a group name, not for IUser.
        return user instanceof User casted
                ? ess.getSettings().getHomeLimit(casted)
                : ESSENTIALS_DEFAULT_LIMIT;
    }

    /**
     * Every tier EssentialsX has configured, ascending by limit.
     *
     * <p>The group names come from the raw {@code sethome-multiple} section because EssentialsX
     * exposes no enumeration API; the limit for each one is then asked of EssentialsX itself
     * ({@code getHomeLimit(String)}), so the value the menu shows is the value EssentialsX would
     * actually apply, including its own default fallbacks.
     */
    private List<HomeTier> tiers(Essentials ess) {
        Map<String, Integer> configured = HomeTier.emptyOrderedMap();
        ConfigurationSection section = ess.getConfig().getConfigurationSection("sethome-multiple");
        if (section != null) {
            for (String group : section.getKeys(false)) {
                configured.put(group, ess.getSettings().getHomeLimit(group));
            }
        }
        if (configured.isEmpty()) {
            configured.put("default", ess.getSettings().getHomeLimit("default"));
        }
        return HomeTier.sorted(configured);
    }

    /**
     * Teleports the player to a home through EssentialsX's own command path.
     *
     * <p>Going through the command rather than moving the player directly is what keeps the warmup,
     * the cooldown, the charge and the {@code UserTeleportHomeEvent} chain intact, which is also what
     * gives this plugin's listener something to hook into. Reimplementing the move would quietly
     * bypass every one of those.
     */
    @Override
    public boolean teleport(Player player, String home) {
        if (!isAvailable()) {
            return false;
        }
        return Bukkit.dispatchCommand(player, "essentials:home " + home);
    }

    /** EssentialsX exposes {@code IUser#renameHome}, so the menu can offer the action. */
    @Override
    public boolean supportsRename() {
        return true;
    }

    /** EssentialsX exposes {@code IUser#delHome}, so the menu can offer the action. */
    @Override
    public boolean supportsDelete() {
        return true;
    }

    /**
     * Renames through {@code IUser#renameHome}, which fires EssentialsX's own
     * {@code HomeModifyEvent} with cause RENAME.
     *
     * <p>That event is what {@code IconLifecycleListener} is already hooked to, so the icon moves to
     * the new name by the same path a {@code /renamehome} would take. Nothing extra is done here:
     * doing the icon move here as well would run it twice, and the second run would look up a
     * source key the first one has already emptied.
     *
     * <p>{@code renameHome} declares {@code throws Exception} — it throws when the old name does not
     * exist — so the broad catch is the interface's, not a precaution.
     */
    @Override
    public boolean rename(Player player, String home, String newName) {
        IUser user = user(player);
        if (user == null) {
            return false;
        }
        try {
            user.renameHome(home, newName);
            return true;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "EssentialsX refused to rename home " + home, ex);
            return false;
        }
    }

    /**
     * Deletes through {@code IUser#delHome}, which fires {@code HomeModifyEvent} with cause DELETE.
     *
     * <p>The icon row is dropped by the lifecycle listener reacting to that event, for the same
     * reason the rename does not move it here.
     */
    @Override
    public boolean delete(Player player, String home) {
        IUser user = user(player);
        if (user == null) {
            return false;
        }
        try {
            user.delHome(home);
            return true;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "EssentialsX refused to delete home " + home, ex);
            return false;
        }
    }

    /** The EssentialsX user for a connected player, or null when EssentialsX is not answering. */
    private @Nullable IUser user(Player player) {
        Essentials ess = essentials();
        return ess == null ? null : ess.getUser(player);
    }
}
