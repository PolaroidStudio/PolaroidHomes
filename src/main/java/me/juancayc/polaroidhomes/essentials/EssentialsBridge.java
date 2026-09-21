package me.juancayc.polaroidhomes.essentials;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import net.ess3.api.IUser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single place this plugin talks to EssentialsX.
 *
 * <p>EssentialsX owns homes and home limits outright: this class reads them and never writes a
 * limit of its own. The limit a player has is whatever {@code Settings.getHomeLimit(User)} resolves,
 * which already walks the {@code sethome-multiple} groups and tests
 * {@code essentials.sethome.multiple.<group>} through Bukkit, taking the highest match. That is why
 * there is no permissions-plugin dependency anywhere in this project: adding one would give two
 * answers to a question that already has one.
 */
public final class EssentialsBridge {

    /** EssentialsX's own last-resort limit when nothing at all is configured. */
    private static final int ESSENTIALS_DEFAULT_LIMIT = 3;

    private final Logger logger;
    private Essentials essentials;

    public EssentialsBridge(Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    /**
     * Resolves the Essentials instance.
     *
     * <p>Looked up lazily rather than cached at construction: paper-plugin.yml declares Essentials
     * as a required BEFORE dependency so it is loaded first, but an operator can still disable it
     * at runtime, and a stale reference to a disabled plugin is worse than no reference.
     */
    public @Nullable Essentials essentials() {
        if (essentials != null && essentials.isEnabled()) {
            return essentials;
        }
        Plugin found = Bukkit.getPluginManager().getPlugin("Essentials");
        essentials = found instanceof Essentials casted && casted.isEnabled() ? casted : null;
        return essentials;
    }

    public boolean isAvailable() {
        return essentials() != null;
    }

    public @Nullable IUser user(Player player) {
        Essentials ess = essentials();
        return ess == null ? null : ess.getUser(player);
    }

    /** The player's home names, in the order EssentialsX stores them. Never null. */
    public List<String> homes(Player player) {
        IUser user = user(player);
        return user == null ? List.of() : List.copyOf(user.getHomes());
    }

    public @Nullable Location home(Player player, String name) {
        IUser user = user(player);
        if (user == null) {
            return null;
        }
        try {
            return user.getHome(name);
        } catch (Exception ex) {
            // EssentialsX throws when the home's world is not loaded. That is an ordinary state on
            // a server with a rotating minigame world, not something worth a stack trace.
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
    public int homeLimit(Player player) {
        Essentials ess = essentials();
        IUser user = user(player);
        // The concrete User is required rather than the interface: getHomeLimit is only overloaded
        // for User and for a group name, not for IUser.
        if (ess == null || !(user instanceof User casted)) {
            return ESSENTIALS_DEFAULT_LIMIT;
        }
        return ess.getSettings().getHomeLimit(casted);
    }

    /**
     * Every tier EssentialsX has configured, ascending by limit.
     *
     * <p>The group names come from the raw {@code sethome-multiple} section because EssentialsX
     * exposes no enumeration API; the limit for each one is then asked of EssentialsX itself
     * ({@code getHomeLimit(String)}), so the value the menu shows is the value EssentialsX would
     * actually apply, including its own default fallbacks.
     */
    public List<HomeTier> tiers() {
        Essentials ess = essentials();
        if (ess == null) {
            return List.of();
        }
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
     * <p>Going through the command rather than moving the player directly is what keeps the
     * warmup, the cooldown, the charge and the {@code UserTeleportHomeEvent} chain intact, which is
     * also what gives this plugin's listener something to hook into. Reimplementing the move would
     * quietly bypass every one of those.
     */
    public boolean teleportHome(Player player, String home) {
        if (!isAvailable()) {
            return false;
        }
        return Bukkit.dispatchCommand(player, "essentials:home " + home);
    }

    // ---------------------------------------------------------------- temporary diagnostics

    /** The raw user object, so a diagnostic can report its concrete class. */
    public @Nullable Object userObject(Player player) {
        Essentials ess = essentials();
        return ess == null ? null : ess.getUser(player);
    }

    /** getHomes() through reflection, to tell a linkage problem from an empty result. */
    public String homesReflective(Player player) {
        Object user = userObject(player);
        if (user == null) {
            return "<no user>";
        }
        try {
            Object result = user.getClass().getMethod("getHomes").invoke(user);
            return String.valueOf(result);
        } catch (Throwable ex) {
            return "<" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ">";
        }
    }

    /** Reads the homes straight out of EssentialsX's own userdata file. */
    public String homesFromDisk(Player player) {
        Essentials ess = essentials();
        if (ess == null) {
            return "<no essentials>";
        }
        java.io.File file = new java.io.File(ess.getDataFolder(),
                "userdata/" + player.getUniqueId() + ".yml");
        if (!file.isFile()) {
            return "<no file: " + file.getPath() + ">";
        }
        ConfigurationSection section = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(file).getConfigurationSection("homes");
        return section == null ? "<no homes section>" : String.valueOf(section.getKeys(false));
    }
}
