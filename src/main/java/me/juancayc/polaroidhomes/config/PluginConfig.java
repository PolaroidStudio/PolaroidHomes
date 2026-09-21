package me.juancayc.polaroidhomes.config;

import me.juancayc.polaroidhomes.effect.catalog.EffectCatalog;
import me.juancayc.polaroidhomes.intercept.CommandInterceptor;
import me.juancayc.polaroidhomes.world.WorldBlacklist;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Typed view over config.yml, re-read whenever the plugin reloads. */
public final class PluginConfig {

    private final Plugin plugin;

    private String defaultIcon;
    private String emptySlotIcon;
    private String lockedSlotIcon;
    private String fillerIcon;
    private List<String> iconChoices;

    private Sound clickSound;
    private float clickVolume;
    private float clickPitch;

    private EffectCatalog effectCatalog;
    private boolean homeEffectsEnabled;
    private boolean tpaEffectsEnabled;
    private double maxEffectSeconds;

    private long saveIntervalTicks;

    private String homeProvider;

    private WorldBlacklist worldBlacklist;
    private CommandInterceptor interceptor;

    public PluginConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // Read as written, and resolved against what is installed by HomeProviders. Validating a
        // name here would need the plugin list, which this type deliberately knows nothing about.
        this.homeProvider = config.getString("hooks.home-provider", "auto");

        this.defaultIcon = config.getString("gui.default-icon", "LIGHT_BLUE_BED");
        this.emptySlotIcon = config.getString("gui.empty-slot-icon", "LIME_STAINED_GLASS_PANE");
        this.lockedSlotIcon = config.getString("gui.locked-slot-icon", "IRON_BARS");
        this.fillerIcon = config.getString("gui.filler-icon", "BLACK_STAINED_GLASS_PANE");
        this.iconChoices = config.getStringList("gui.icon-choices");

        this.clickSound = parseSound(config.getString("gui.click-sound"));
        this.clickVolume = (float) config.getDouble("gui.click-sound-volume", 0.6D);
        this.clickPitch = (float) config.getDouble("gui.click-sound-pitch", 1.2D);

        ConfigurationSection effects = config.getConfigurationSection("teleport-effects");
        // The whole catalog, both categories, parsed here so a reload picks up a new product
        // without a restart. Which of these a given player sees is decided per player by their
        // permissions, not by any value in this file.
        this.effectCatalog = EffectCatalog.from(effects);
        // Which kinds of teleport are decorated at all. These replace the old `mode`/`fallback`
        // pair and the `tpa.enabled` block: a catalog entry's category already says how it renders,
        // so the only thing left for the file to say is where effects apply.
        this.homeEffectsEnabled = config.getBoolean("teleport-effects.homes", true);
        this.tpaEffectsEnabled = config.getBoolean("teleport-effects.tpa", false);
        this.maxEffectSeconds = Math.max(1.0D,
                config.getDouble("teleport-effects.max-effect-seconds", 10.0D));

        this.worldBlacklist = WorldBlacklist.of(config.getStringList("worlds.blacklist"));

        // Read as an explicit two-key lookup rather than by walking the section's children: the
        // section's comments document exactly two labels, and a third one an operator added would
        // otherwise be silently honoured, intercepting a command nothing in this plugin can serve.
        Map<String, Boolean> intercept = new LinkedHashMap<>();
        intercept.put("homes", config.getBoolean("commands.intercept.homes", true));
        intercept.put("home", config.getBoolean("commands.intercept.home", false));
        this.interceptor = CommandInterceptor.of(intercept);

        long intervalSeconds = Math.max(5L, config.getLong("icons.save-interval-seconds", 120L));
        this.saveIntervalTicks = intervalSeconds * 20L;
    }

    private static @Nullable Sound parseSound(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Sound is a registry-backed interface in modern Paper, so a direct valueOf is not
        // available. The registry lookup accepts the same names the enum used to.
        try {
            return org.bukkit.Registry.SOUNDS.get(
                    org.bukkit.NamespacedKey.minecraft(raw.trim().toLowerCase(Locale.ROOT)));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * The raw {@code hooks.home-provider} value.
     *
     * <p>Not re-read on reload in any way that matters: the provider is chosen once on enable and the
     * listeners registered for it cannot be swapped without a restart, so changing this key mid-session
     * takes effect on the next start. See {@code PolaroidHomesPlugin#warnIfProviderChanged}.
     */
    public String homeProvider() {
        return homeProvider;
    }

    public String defaultIcon() {
        return defaultIcon;
    }

    public String emptySlotIcon() {
        return emptySlotIcon;
    }

    public String lockedSlotIcon() {
        return lockedSlotIcon;
    }

    public String fillerIcon() {
        return fillerIcon;
    }

    public List<String> iconChoices() {
        return iconChoices;
    }

    public @Nullable Sound clickSound() {
        return clickSound;
    }

    public float clickVolume() {
        return clickVolume;
    }

    public float clickPitch() {
        return clickPitch;
    }

    /** Every effect an operator has declared, in both categories. Never null. */
    public EffectCatalog effectCatalog() {
        return effectCatalog;
    }

    /** Whether a home teleport is decorated with the traveller's equipped effect. */
    public boolean homeEffectsEnabled() {
        return homeEffectsEnabled;
    }

    /**
     * Whether an accepted {@code /tpa} is decorated at all.
     *
     * <p>Never applies to {@code /tpahere}: that direction moves the recipient, and the effect
     * follows whoever travels, so a tpahere has nothing this plugin decorates regardless of this
     * switch.
     */
    public boolean tpaEffectsEnabled() {
        return tpaEffectsEnabled;
    }

    public double maxEffectSeconds() {
        return maxEffectSeconds;
    }

    public long saveIntervalTicks() {
        return saveIntervalTicks;
    }

    /** The worlds homes are not allowed in. Never null; an unconfigured list blocks nothing. */
    public WorldBlacklist worldBlacklist() {
        return worldBlacklist;
    }

    /** Which typed command labels open the menu instead of reaching their owner. */
    public CommandInterceptor interceptor() {
        return interceptor;
    }
}
