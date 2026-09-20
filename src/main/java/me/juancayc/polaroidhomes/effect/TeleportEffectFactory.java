package me.juancayc.polaroidhomes.effect;

import me.juancayc.polaroidhomes.config.EffectMode;
import me.juancayc.polaroidhomes.config.PluginConfig;
import org.bukkit.plugin.Plugin;

/** Picks the effect implementation, falling back when the requested one cannot be built. */
public final class TeleportEffectFactory {

    private TeleportEffectFactory() {
    }

    public static TeleportEffect create(Plugin plugin, PluginConfig config) {
        EffectMode requested = config.mode();
        if (requested == EffectMode.MODEL_ENGINE && !ModelEngineEffect.isAvailable()) {
            plugin.getLogger().info("Model Engine is not available; using the configured fallback ("
                    + config.fallbackMode().name().toLowerCase(java.util.Locale.ROOT) + ").");
            requested = config.fallbackMode();
        }
        return switch (requested) {
            case MODEL_ENGINE -> new ModelEngineEffect(plugin, config.maxEffectSeconds());
            case PARTICLES -> new ParticleEffect();
            case NONE -> new NoneEffect();
        };
    }
}
