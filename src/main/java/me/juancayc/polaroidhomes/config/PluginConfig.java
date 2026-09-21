package me.juancayc.polaroidhomes.config;

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

    private EffectMode mode;
    private EffectMode fallbackMode;
    private TeleportEffects homeEffects;
    private TeleportEffects tpaEffects;
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

        // A mode of model-engine is only honoured if Model Engine actually answered the call, which
        // the effect factory decides. Here the config value is read as written.
        this.mode = EffectMode.parse(config.getString("teleport-effects.mode"), EffectMode.PARTICLES);
        EffectMode configuredFallback =
                EffectMode.parse(config.getString("teleport-effects.fallback"), EffectMode.PARTICLES);
        // A fallback of model-engine would be circular: it is the thing that just failed.
        this.fallbackMode = configuredFallback == EffectMode.MODEL_ENGINE
                ? EffectMode.PARTICLES
                : configuredFallback;

        ConfigurationSection effects = config.getConfigurationSection("teleport-effects");
        this.homeEffects = TeleportEffects.from(effects);
        // The tpa block inherits from the home block rather than from the shipped defaults, so an
        // operator who tuned the home effect and wants the same look for /tpa writes nothing at all.
        this.tpaEffects = TeleportEffects.from(section(effects, "tpa"), homeEffects);
        // On for a fresh install, which chose this plugin for its teleport effects. An existing
        // server gets false written into its file by the v4 -> v5 migration instead, so upgrading
        // never changes what players already see. Read here rather than checked in the listener so
        // a reload can turn it on and off without re-registering anything.
        this.tpaEffectsEnabled = config.getBoolean("teleport-effects.tpa.enabled", true);
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

    private static @Nullable ConfigurationSection section(@Nullable ConfigurationSection parent,
                                                          String key) {
        return parent == null ? null : parent.getConfigurationSection(key);
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

    public EffectMode mode() {
        return mode;
    }

    public EffectMode fallbackMode() {
        return fallbackMode;
    }

    public EffectSettings entry() {
        return homeEffects.entry();
    }

    public EffectSettings arrival() {
        return homeEffects.arrival();
    }

    /** Entry and arrival for an accepted {@code /tpa}, resolved against the home settings. */
    public TeleportEffects tpaEffects() {
        return tpaEffects;
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
