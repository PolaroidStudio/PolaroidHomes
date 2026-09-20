package me.juancayc.polaroidhomes;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.juancayc.polaroidhomes.command.HomesCommand;
import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.config.migration.ConfigMigrations;
import me.juancayc.polaroidhomes.effect.ModelEngineEffect;
import me.juancayc.polaroidhomes.effect.TeleportEffect;
import me.juancayc.polaroidhomes.effect.TeleportEffectFactory;
import me.juancayc.polaroidhomes.essentials.EssentialsBridge;
import me.juancayc.polaroidhomes.icon.IconStorage;
import me.juancayc.polaroidhomes.icon.LegacyIconImport;
import me.juancayc.polaroidhomes.icon.SqlIconStorage;
import me.juancayc.polaroidhomes.item.ItemManager;
import me.juancayc.polaroidhomes.listener.EffectMarkerSweepListener;
import me.juancayc.polaroidhomes.listener.IconCacheListener;
import me.juancayc.polaroidhomes.listener.IconLifecycleListener;
import me.juancayc.polaroidhomes.listener.MenuItemCleanupListener;
import me.juancayc.polaroidhomes.listener.MenuListener;
import me.juancayc.polaroidhomes.listener.TeleportListener;
import me.juancayc.polaroidhomes.menu.HomesMenu;
import me.juancayc.polaroidhomes.menu.MenuContext;
import me.juancayc.polaroidhomes.menu.MenuItemMarker;
import me.juancayc.polaroidhomes.menu.MenuItems;
import me.juancayc.polaroidhomes.menu.MenuRegistry;
import me.juancayc.polaroidhomes.storage.DatabaseManager;
import me.juancayc.polaroidhomes.storage.StorageException;
import me.juancayc.polaroidhomes.storage.StorageSettings;
import me.juancayc.polaroidhomes.text.ColorFormats;
import me.juancayc.polaroidhomes.text.MessageService;
import me.juancayc.polaroidhomes.text.MiniMessageFactory;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

/** Entry point. Wires the services, registers the listeners and owns their lifecycle. */
public final class PolaroidHomesPlugin extends JavaPlugin {

    private PluginConfig config;
    private MessageService messages;
    private EssentialsBridge essentials;
    private DatabaseManager database;
    private SqlIconStorage icons;
    private IconCacheListener iconCache;
    private ItemManager items;
    private MenuRegistry registry;
    private MenuItemMarker marker;
    private MenuContext menuContext;
    private TeleportEffect effect;
    private TeleportListener teleportListener;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("data.yml", false);
        // Runs before anything reads a value: a v0 file that has not been brought forward yet
        // would otherwise be read with its old key layout and silently fall back to defaults.
        ConfigMigrations.runAll(this);

        // One MiniMessage instance for the whole plugin, built before anything renders: a glyph tag
        // in a config value must resolve the same in a title, an item name and a chat line.
        ColorFormats.init(MiniMessageFactory.build());

        this.config = new PluginConfig(this);
        this.messages = new MessageService(this);
        this.essentials = new EssentialsBridge(this);
        if (!openStorage()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.items = ItemManager.withDefaultHooks();
        this.registry = new MenuRegistry(this);
        this.marker = new MenuItemMarker(this);

        if (!essentials.isAvailable()) {
            // Declared required in paper-plugin.yml, so reaching here means an operator disabled it
            // at runtime. Refusing to enable is honest: every feature of this plugin reads from it.
            getLogger().severe("EssentialsX is not enabled. PolaroidHomes cannot run without it.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        rebuildContext();
        this.effect = TeleportEffectFactory.create(this, config);

        registerListeners();
        registerCommand();
        scheduleSaves();
    }

    @Override
    public void onDisable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (registry != null) {
            // Closed rather than left open: a window whose handlers this plugin no longer serves
            // looks functional and does nothing.
            registry.closeAll();
        }
        if (effect != null) {
            // Last chance to take spawned markers with us. Skipping it is how ghost entities
            // accumulate across reloads.
            effect.shutdown();
        }
        if (teleportListener != null) {
            teleportListener.clear();
        }
        if (icons != null) {
            try {
                icons.close();
            } catch (StorageException ex) {
                getLogger().log(Level.SEVERE, "Could not write pending icon changes on shutdown.", ex);
            }
        }
        if (database != null) {
            // Closed last, and only here: the pool outlives every reload on purpose.
            database.close();
            database = null;
        }
    }

    /**
     * Opens the pool and the icon store once, on enable.
     *
     * <p>Deliberately not reachable from the reload command. Reopening a pool re-runs driver
     * discovery and the schema check, and orphans any connection an in-flight flush is holding.
     *
     * @return false when the backend could not be opened, which is fatal: every icon this plugin
     *         renders comes from it
     */
    private boolean openStorage() {
        File dataFile = new File(getDataFolder(), "data.yml");
        StorageSettings settings =
                StorageSettings.from(YamlConfiguration.loadConfiguration(dataFile));
        try {
            this.database = new DatabaseManager(settings, getDataFolder());
            this.icons = new SqlIconStorage(database);
            // RuntimeException covers StorageException and Hikari's own pool-initialisation
            // failure alike; both mean the same thing here, and neither is recoverable.
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Could not open the icon database. "
                    + "Check data.yml and the server's log for the driver error.", ex);
            return false;
        }

        try {
            LegacyIconImport.run(new File(getDataFolder(), "icons.yml"), icons, getLogger());
        } catch (StorageException ex) {
            // The legacy file is left in place, so the import simply runs again next enable.
            getLogger().log(Level.SEVERE, "Could not import the legacy icons.yml.", ex);
        }
        return true;
    }

    private void rebuildContext() {
        MenuItems menuItems = new MenuItems(items, messages, marker);
        this.menuContext = new MenuContext(this, config, messages, items, menuItems, registry,
                essentials, icons);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new MenuListener(this, registry, marker), this);
        getServer().getPluginManager().registerEvents(
                new MenuItemCleanupListener(marker), this);
        getServer().getPluginManager().registerEvents(
                new IconLifecycleListener(icons), this);

        this.iconCache = new IconCacheListener(this, icons);
        getServer().getPluginManager().registerEvents(iconCache, this);

        this.teleportListener = new TeleportListener(this, config, effect);
        getServer().getPluginManager().registerEvents(teleportListener, this);

        if (effect instanceof ModelEngineEffect modelEngine) {
            // Only registered for the effect that actually spawns entities; the other two have
            // nothing that could be orphaned.
            getServer().getPluginManager().registerEvents(
                    new EffectMarkerSweepListener(modelEngine), this);
        }
    }

    /**
     * Registers {@code /homes} through the COMMANDS lifecycle event.
     *
     * <p>Paper plugins cannot declare commands in {@code paper-plugin.yml}; that file has no
     * {@code commands:} section at all, and {@code getCommand()} throws
     * {@code UnsupportedOperationException} during startup rather than returning null. So the
     * label, description and aliases that would have lived in YAML are passed here instead, and
     * this is the single source of truth for them.
     */
    private void registerCommand() {
        HomesCommand command = new HomesCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        "homes",
                        "Open the homes menu.",
                        List.of("phomes", "homemenu"),
                        command));
    }

    /**
     * Starts the async write-behind flush.
     *
     * <p>Asynchronous because JDBC blocks, and the exception is swallowed on purpose: Bukkit
     * cancels a repeating task whose body throws, so letting a transient database error escape
     * would silently stop every future save. The rows stay dirty, so the next tick retries.
     */
    private void scheduleSaves() {
        long interval = config.saveIntervalTicks();
        this.saveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                icons.flush();
            } catch (StorageException ex) {
                getLogger().log(Level.WARNING,
                        "Could not write pending icon changes; retrying on the next save.", ex);
            }
        }, interval, interval);
    }

    /**
     * Re-reads the configuration and rebuilds everything derived from it.
     *
     * <p>Open windows are closed first. Their slot map was built against the previous configuration
     * and their inventory was sized from the previous grid, so redrawing them in place would give a
     * window whose buttons no longer match its size.
     */
    public void reloadEverything() {
        registry.closeAll();

        config.reload();
        messages.reload();
        warnIfBackendChanged();

        // The old effect is shut down before the new one exists, so its markers are removed while
        // the object that knows about them is still the live one.
        if (effect != null) {
            effect.shutdown();
        }
        if (teleportListener != null) {
            HandlerList.unregisterAll(teleportListener);
            teleportListener.clear();
        }
        this.effect = TeleportEffectFactory.create(this, config);
        this.teleportListener = new TeleportListener(this, config, effect);
        getServer().getPluginManager().registerEvents(teleportListener, this);

        rebuildContext();

        if (saveTask != null) {
            saveTask.cancel();
        }
        scheduleSaves();
    }

    /**
     * Re-reads data.yml and warns when the operator pointed it at a different database.
     *
     * <p>The open pool is kept either way. Swapping a live {@code HikariDataSource} mid-session
     * would strand in-flight flushes on a source nobody closes, and the cache in front of it was
     * populated from the old backend, so the honest answer is a restart.
     */
    private void warnIfBackendChanged() {
        if (database == null) {
            return;
        }
        StorageSettings current = StorageSettings.from(
                YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml")));
        if (!database.settings().describesSamePool(current)) {
            getLogger().warning("data.yml now names a different database than the one currently "
                    + "open. The existing connection is kept; restart the server to apply it.");
        }
    }

    /**
     * Opens the homes grid for a viewer, showing the target's homes.
     *
     * <p>A target whose rows are not cached — an admin viewing an offline player — is loaded off
     * the main thread first, and the window is only built once the icons are there. Opening
     * immediately would render every home with the default icon and look like data loss.
     */
    public void openHomesMenu(Player viewer, OfflinePlayer target) {
        if (icons.isLoaded(target.getUniqueId())) {
            registry.open(viewer, new HomesMenu(menuContext, viewer, target, this::teleportHome));
            return;
        }
        iconCache.loadAsync(target.getUniqueId(), () -> {
            if (viewer.isOnline()) {
                registry.open(viewer, new HomesMenu(menuContext, viewer, target, this::teleportHome));
            }
        });
    }

    /**
     * Runs the teleport through EssentialsX so its warmup, cooldown and event chain stay intact.
     * The effects hang off that chain, so bypassing it would silence them too.
     */
    public void teleportHome(Player player, String home) {
        if (!essentials.teleportHome(player, home)) {
            messages.send(player, "homes.teleport_failed", "%home%", MessageService.escape(home));
        }
    }

    public MessageService messages() {
        return messages;
    }

    public EssentialsBridge essentials() {
        return essentials;
    }

    public PluginConfig config() {
        return config;
    }

    public IconStorage icons() {
        return icons;
    }
}
