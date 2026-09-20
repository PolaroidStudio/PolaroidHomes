package me.juancayc.polaroidhomes.config;

import java.util.Locale;

/** Which teleport effect implementation the operator asked for. */
public enum EffectMode {

    MODEL_ENGINE,
    PARTICLES,
    NONE;

    /** Parses a config value; anything unrecognised falls back rather than failing to enable. */
    public static EffectMode parse(String raw, EffectMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "model-engine", "modelengine", "model_engine" -> MODEL_ENGINE;
            case "particles", "particle" -> PARTICLES;
            case "none", "off", "disabled" -> NONE;
            default -> fallback;
        };
    }
}
