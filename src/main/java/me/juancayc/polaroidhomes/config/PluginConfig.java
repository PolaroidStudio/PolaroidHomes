package me.juancayc.polaroidhomes.config;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

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
    private EffectSettings entry;
    private EffectSettings arrival;
    private double maxEffectSeconds;

    private long saveIntervalTicks;

    private String homeProvider;

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
        this.entry = EffectSettings.from(section(effects, "entry"), Particle.PORTAL, 2.0D);
        this.arrival = EffectSettings.from(section(effects, "arrival"), Particle.END_ROD, 1.5D);
        this.maxEffectSeconds = Math.max(1.0D,
                config.getDouble("teleport-effects.max-effect-seconds", 10.0D));

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
        return entry;
    }

    public EffectSettings arrival() {
        return arrival;
    }

    public double maxEffectSeconds() {
        return maxEffectSeconds;
    }

    public long saveIntervalTicks() {
        return saveIntervalTicks;
    }
}
