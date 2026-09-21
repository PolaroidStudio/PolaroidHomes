package me.juancayc.polaroidhomes;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.juancayc.polaroidhomes.command.HomesCommand;
import me.juancayc.polaroidhomes.config.MenuConfig;
import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.config.migration.ConfigMigrations;
import me.juancayc.polaroidhomes.effect.ModelEngineEffect;
import me.juancayc.polaroidhomes.effect.TeleportEffect;
import me.juancayc.polaroidhomes.effect.TeleportEffectFactory;
import me.juancayc.polaroidhomes.icon.IconStorage;
import me.juancayc.polaroidhomes.icon.LegacyIconImport;
import me.juancayc.polaroidhomes.icon.SqlIconStorage;
import me.juancayc.polaroidhomes.item.ItemManager;
import me.juancayc.polaroidhomes.listener.EffectMarkerSweepListener;
import me.juancayc.polaroidhomes.listener.HuskHomesIconLifecycleListener;
import me.juancayc.polaroidhomes.listener.HuskHomesTeleportListener;
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
import me.juancayc.polaroidhomes.provider.HomeProvider;
import me.juancayc.polaroidhomes.provider.HomeProviders;
import me.juancayc.polaroidhomes.provider.ProviderSelection;
import me.juancayc.polaroidhomes.storage.DatabaseManager;
import me.juancayc.polaroidhomes.storage.StorageException;
import me.juancayc.polaroidhomes.storage.StorageSettings;
import me.juancayc.polaroidhomes.text.ColorFormats;
import me.juancayc.polaroidhomes.text.MessageService;
import me.juancayc.polaroidhomes.text.MiniMessageFactory;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

/** Entry point. Wires the services, registers the listeners and owns their lifecycle. */
public final class PolaroidHomesPlugin extends JavaPlugin {

    private PluginConfig config;
    private MenuConfig menus;
    private MessageService messages;
    private HomeProvider provider;
    /** The provider-specific icon-lifecycle listener, kept only so a reload can unregister it. */
    private Listener iconLifecycle;
    private String selectedProviderRequest;
    private DatabaseManager database;
    private SqlIconStorage icons;
    private IconCacheListener iconCache;
    private ItemManager items;
    private MenuRegistry registry;
    private MenuItemMarker marker;
    private MenuContext menuContext;
    private TeleportEffect effect;
    /**
     * The provider-specific teleport listener, kept only so a reload can unregister it.
     *
     * <p>Typed as Listener because the two providers have unrelated listener classes: the effects the
     * plugin plays are the same, but the event chains they hang off share no supertype.
     */
    private Listener teleportListener;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("data.yml", false);
        saveResource("menu.yml", false);
        // Runs before anything reads a value: a v0 file that has not been brought forward yet
        // would otherwise be read with its old key layout and silently fall back to defaults.
        ConfigMigrations.runAll(this);

        // One MiniMessage instance for the whole plugin, built before anything renders: a glyph tag
        // in a config value must resolve the same in a title, an item name and a chat line.
        ColorFormats.init(MiniMessageFactory.build());

        this.config = new PluginConfig(this);
        // Read after config.yml so a migrated max-displayed-slots is already on disk in menu.yml.
        this.menus = new MenuConfig(this);
        this.messages = new MessageService(this);
        if (!selectProvider()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!openStorage()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.items = ItemManager.withDefaultHooks();
        this.registry = new MenuRegistry(this);
        this.marker = new MenuItemMarker(this);

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
        clearTeleportListener();
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
     * Resolves {@code hooks.home-provider} against the plugins actually installed.
     *
     * <p>Run before storage opens, because a server with no home backend has nothing for this plugin
     * to do and should not pay for a connection pool on its way to being disabled.
     *
     * <p>Every failure is logged as SEVERE with the file, the key and the values that would work,
     * because the operator reading it is looking at a server that just refused to enable a plugin.
     * Nothing falls back silently: that is the bug this whole abstraction exists to prevent — the
     * plugin used to assume EssentialsX, read an empty home list from it on a HuskHomes server, and
     * render a perfectly correct empty grid while the player's real homes sat in another plugin.
     *
     * @return false when no usable provider was found, which is fatal
     */
    private boolean selectProvider() {
        this.selectedProviderRequest = config.homeProvider();
        List<HomeProvider> candidates = HomeProviders.candidates(this);
        ProviderSelection selection =
                ProviderSelection.resolve(selectedProviderRequest, candidates);
        String line = selection.describe(candidates);
        if (!selection.isResolved()) {
            getLogger().severe(line);
            return false;
        }
        this.provider = selection.chosen();
        getLogger().info(line);
        String ambiguity = selection.ambiguityWarning(candidates);
        if (ambiguity != null) {
            getLogger().warning(ambiguity);
        }
        if (!provider.supportsTiers()) {
            // Said once, at enable, rather than left for an operator to discover from a menu that
            // looks like it lost a feature.
            getLogger().info(provider.pluginName() + " does not expose the ranks that grant home "
                    + "limits, so locked slots will not name one. Home limits still come from "
                    + provider.pluginName() + " itself.");
        }
        return true;
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
        MenuItems menuItems = new MenuItems(items, messages, marker, getLogger());
        this.menuContext = new MenuContext(this, config, menus, messages, items, menuItems,
                registry, provider, icons);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new MenuListener(this, registry, marker), this);
        getServer().getPluginManager().registerEvents(
                new MenuItemCleanupListener(marker), this);
        // Only the selected provider's listeners are registered. Registering both would need the
        // other provider's event classes to resolve, which fails with a NoClassDefFoundError on a
        // server that does not have that plugin installed.
        this.iconLifecycle = iconLifecycleListener();
        getServer().getPluginManager().registerEvents(iconLifecycle, this);

        this.iconCache = new IconCacheListener(this, icons);
        getServer().getPluginManager().registerEvents(iconCache, this);

        this.teleportListener = teleportListener();
        getServer().getPluginManager().registerEvents(teleportListener, this);

        if (effect instanceof ModelEngineEffect modelEngine) {
            // Only registered for the effect that actually spawns entities; the other two have
            // nothing that could be orphaned.
            getServer().getPluginManager().registerEvents(
                    new EffectMarkerSweepListener(modelEngine), this);
        }
    }

    /**
     * The icon-lifecycle listener for the selected provider.
     *
     * <p>Split by provider because neither backend has a common event model: EssentialsX announces a
     * rename through one {@code HomeModifyEvent} with a cause, and HuskHomes leaves it to be inferred
     * from the before-and-after homes on a {@code HomeEditEvent}. A single listener would have to
     * reference both, and a class referencing an absent plugin's events cannot be loaded.
     */
    private Listener iconLifecycleListener() {
        return "huskhomes".equals(provider.id())
                ? new HuskHomesIconLifecycleListener(icons)
                : new IconLifecycleListener(icons);
    }

    private Listener teleportListener() {
        return "huskhomes".equals(provider.id())
                ? new HuskHomesTeleportListener(this, config, effect)
                : new TeleportListener(this, config, effect);
    }

    /** Both listeners hold a pending set that must not survive the effect they decorate. */
    private void clearTeleportListener() {
        if (teleportListener instanceof TeleportListener essentials) {
            essentials.clear();
        } else if (teleportListener instanceof HuskHomesTeleportListener husk) {
            husk.clear();
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
     *
     * <p>The label is deliberately NOT {@code homes}: EssentialsX registers {@code homes} as an
     * alias of its own {@code /home}, and it loads first because this plugin declares it as a
     * required BEFORE dependency. Registering {@code homes} here therefore lost the race silently
     * — players ran {@code /homes}, EssentialsX answered, and this menu never opened. Every label
     * used here was checked against EssentialsX's own command list; {@code home}, {@code homes},
     * {@code ehome}, {@code ehomes}, {@code sethome}, {@code createhome}, {@code delhome},
     * {@code remhome}, {@code rmhome} and {@code renamehome} are all taken.
     */
    private void registerCommand() {
        HomesCommand command = new HomesCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        "homemenu",
                        "Open the homes menu.",
                        List.of("phomes", "homesmenu", "hmenu"),
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
        // Re-read here, not only on enable: a layout change is the most common reason an operator
        // runs /homes reload. Every open window was already closed above, so the new template only
        // ever sizes a window built after this point — an open one cannot be resized in place.
        menus.reload();
        messages.reload();
        warnIfBackendChanged();
        boolean providerChanged = reselectProvider();

        // The old effect is shut down before the new one exists, so its markers are removed while
        // the object that knows about them is still the live one.
        if (effect != null) {
            effect.shutdown();
        }
        if (teleportListener != null) {
            HandlerList.unregisterAll(teleportListener);
            clearTeleportListener();
        }
        this.effect = TeleportEffectFactory.create(this, config);
        this.teleportListener = teleportListener();
        getServer().getPluginManager().registerEvents(teleportListener, this);

        // The icon-lifecycle listener is per provider too, so a provider swap has to re-hook it or
        // renames and deletes would keep being read from the backend that is no longer in use.
        // Only rebuilt when the provider actually changed: its listener is otherwise stateless and
        // re-registering it every reload would be churn for nothing.
        if (providerChanged) {
            if (iconLifecycle != null) {
                HandlerList.unregisterAll(iconLifecycle);
            }
            this.iconLifecycle = iconLifecycleListener();
            getServer().getPluginManager().registerEvents(iconLifecycle, this);
        }

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
     * Warns when an operator changed the home provider without restarting.
     *
     * <p>The selection is not reapplied. The listeners registered for a provider reference that
     * plugin's event classes, and the icons already in the store are keyed to the home names that
     * backend gave out; switching mid-session would leave live listeners for one backend, a menu
     * reading another, and icons attributed to whichever was in use when they were picked. A restart
     * is the honest answer, exactly as it is for a changed database.
     */
    /**
     * Applies a changed {@code hooks.home-provider} without a restart.
     *
     * <p>Switching backend only needs the provider object swapped and its two provider-specific
     * listeners re-hooked, both of which this class already does for the teleport listener on every
     * reload. Making an operator restart the server to correct one config line — most often because
     * {@code auto} picked the backend that happens to be empty — was a limitation of this code, not
     * of the backends.
     *
     * <p>What genuinely does not survive the swap is the icon store: rows are keyed to a backend's
     * home names, so after a switch an icon only reappears on a home whose name matches. Nothing is
     * deleted, so switching back restores them. That is said out loud rather than prevented.
     *
     * @return true when the live provider was replaced
     */
    private boolean reselectProvider() {
        String requested = config.homeProvider();
        if (requested.equalsIgnoreCase(selectedProviderRequest)) {
            return false;
        }

        List<HomeProvider> candidates = HomeProviders.candidates(this);
        ProviderSelection selection = ProviderSelection.resolve(requested, candidates);
        if (!selection.isResolved()) {
            // Refused rather than applied: the running provider is working, and dropping it for one
            // that cannot be resolved would turn a typo into an unusable menu.
            getLogger().severe(selection.describe(candidates)
                    + " Keeping " + provider.pluginName() + " until this is corrected.");
            return false;
        }
        if (selection.chosen().id().equals(provider.id())) {
            // The request changed but resolves to the same backend, e.g. auto -> essentialsx.
            this.selectedProviderRequest = requested;
            return false;
        }

        String previous = provider.pluginName();
        this.provider = selection.chosen();
        this.selectedProviderRequest = requested;
        getLogger().info(selection.describe(candidates)
                + " Switched from " + previous + " without a restart.");
        getLogger().warning("Stored icons are keyed to the home names of the backend they were "
                + "chosen under, so an icon set under " + previous + " only shows again on a home "
                + "of the same name. Nothing was deleted.");
        if (!provider.supportsTiers()) {
            getLogger().info(provider.pluginName() + " does not expose the ranks behind its home "
                    + "limits, so locked slots name no rank.");
        }
        return true;
    }

    /**
     * Opens the homes grid for a viewer, showing the target's homes.
     *
     * <p>Two things have to be in memory before an inventory can be built, and neither can be read on
     * the render path. The icons come from SQL, which blocks; the homes come from the provider, which
     * for HuskHomes answers with a future its own documentation forbids blocking on. So both are
     * resolved first and the window is opened in the callback — the icon load was already doing this,
     * and the home read now rides the same pattern.
     *
     * <p>A synchronous provider costs nothing extra here: EssentialsX hands back an already-completed
     * future, so its {@code thenAccept} runs inline on the calling thread and the window opens in the
     * same tick the command was typed.
     */
    public void openHomesMenu(Player viewer, OfflinePlayer target) {
        if (icons.isLoaded(target.getUniqueId())) {
            openWithSnapshot(viewer, target);
            return;
        }
        iconCache.loadAsync(target.getUniqueId(), () -> {
            if (viewer.isOnline()) {
                openWithSnapshot(viewer, target);
            }
        });
    }

    /**
     * Reads the homes, then opens the window on the main thread.
     *
     * <p>The hop back through the scheduler is not optional: the provider's future may complete on its
     * own executor, and {@code Bukkit.createInventory} plus {@code openInventory} are main-thread-only.
     * {@code isPrimaryThread} keeps the synchronous provider's inline completion from paying for a
     * scheduler round trip it does not need.
     */
    private void openWithSnapshot(Player viewer, OfflinePlayer target) {
        provider.snapshot(viewer, target).thenAccept(snapshot -> onMainThread(() -> {
            if (viewer.isOnline()) {
                registry.open(viewer,
                        new HomesMenu(menuContext, viewer, target, snapshot, this::teleportHome));
            }
        }));
    }

    private void onMainThread(Runnable action) {
        if (getServer().isPrimaryThread()) {
            action.run();
        } else {
            getServer().getScheduler().runTask(this, action);
        }
    }

    /**
     * Runs the teleport through the provider so its warmup, cooldown and event chain stay intact.
     * The effects hang off that chain, so bypassing it would silence them too.
     */
    public void teleportHome(Player player, String home) {
        if (!provider.teleport(player, home)) {
            messages.send(player, "homes.teleport_failed", "%home%", MessageService.escape(home));
        }
    }

    public MessageService messages() {
        return messages;
    }

    public HomeProvider provider() {
        return provider;
    }

    public PluginConfig config() {
        return config;
    }

    public IconStorage icons() {
        return icons;
    }
}
