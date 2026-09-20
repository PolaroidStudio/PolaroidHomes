package me.juancayc.polaroidhomes;

import me.juancayc.polaroidhomes.command.HomesCommand;
import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.effect.ModelEngineEffect;
import me.juancayc.polaroidhomes.effect.TeleportEffect;
import me.juancayc.polaroidhomes.effect.TeleportEffectFactory;
import me.juancayc.polaroidhomes.essentials.EssentialsBridge;
import me.juancayc.polaroidhomes.icon.IconStorage;
import me.juancayc.polaroidhomes.icon.YamlIconStorage;
import me.juancayc.polaroidhomes.item.ItemManager;
import me.juancayc.polaroidhomes.listener.EffectMarkerSweepListener;
import me.juancayc.polaroidhomes.listener.IconLifecycleListener;
import me.juancayc.polaroidhomes.listener.MenuItemCleanupListener;
import me.juancayc.polaroidhomes.listener.MenuListener;
import me.juancayc.polaroidhomes.listener.TeleportListener;
import me.juancayc.polaroidhomes.menu.HomesMenu;
import me.juancayc.polaroidhomes.menu.MenuContext;
import me.juancayc.polaroidhomes.menu.MenuItemMarker;
import me.juancayc.polaroidhomes.menu.MenuItems;
import me.juancayc.polaroidhomes.menu.MenuRegistry;
import me.juancayc.polaroidhomes.text.ColorFormats;
import me.juancayc.polaroidhomes.text.MessageService;
import me.juancayc.polaroidhomes.text.MiniMessageFactory;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

/** Entry point. Wires the services, registers the listeners and owns their lifecycle. */
public final class PolaroidHomesPlugin extends JavaPlugin {

    private PluginConfig config;
    private MessageService messages;
    private EssentialsBridge essentials;
    private IconStorage icons;
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

        // One MiniMessage instance for the whole plugin, built before anything renders: a glyph tag
        // in a config value must resolve the same in a title, an item name and a chat line.
        ColorFormats.init(MiniMessageFactory.build());

        this.config = new PluginConfig(this);
        this.messages = new MessageService(this);
        this.essentials = new EssentialsBridge(this);
        this.icons = new YamlIconStorage(new File(getDataFolder(), "icons.yml"), getLogger());
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
            icons.close();
        }
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

        this.teleportListener = new TeleportListener(this, config, effect);
        getServer().getPluginManager().registerEvents(teleportListener, this);

        if (effect instanceof ModelEngineEffect modelEngine) {
            // Only registered for the effect that actually spawns entities; the other two have
            // nothing that could be orphaned.
            getServer().getPluginManager().registerEvents(
                    new EffectMarkerSweepListener(modelEngine), this);
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("homes");
        if (command == null) {
            getLogger().severe("The /homes command is missing from paper-plugin.yml.");
            return;
        }
        HomesCommand executor = new HomesCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void scheduleSaves() {
        long interval = config.saveIntervalTicks();
        this.saveTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this, () -> icons.flush(), interval, interval);
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

    /** Opens the homes grid for a viewer, showing the target's homes. */
    public void openHomesMenu(Player viewer, OfflinePlayer target) {
        registry.open(viewer, new HomesMenu(menuContext, viewer, target, this::teleportHome));
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
