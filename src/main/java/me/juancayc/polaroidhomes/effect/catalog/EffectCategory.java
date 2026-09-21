package me.juancayc.polaroidhomes.effect.catalog;

import java.util.Locale;

/**
 * The two kinds of purchasable effect.
 *
 * <p>A category is not a rendering preference the operator picks per server; it is what an entry
 * <em>is</em>. An animation entry names a Model Engine model, a particle entry names a vanilla
 * particle, and neither can be reinterpreted as the other. That is why the old
 * {@code teleport-effects.mode} key is gone: the mode used to be a global switch between two
 * renderers, and it is now a property of each catalog entry.
 *
 * <p>The category also decides the permission namespace, which is the node an operator sells from
 * a shop plugin. Keeping the two in one place is what makes
 * {@code polaroidhomes.animation.<id>} impossible to misspell — nothing anywhere writes that
 * string by hand.
 */
public enum EffectCategory {

    /** A Model Engine model plus animation. Unavailable when Model Engine is not installed. */
    ANIMATION("animations", "polaroidhomes.animation."),

    /** Vanilla particles. Always available, since nothing outside the server is needed. */
    PARTICLE("particles", "polaroidhomes.particle.");

    private final String configKey;
    private final String permissionPrefix;

    EffectCategory(String configKey, String permissionPrefix) {
        this.configKey = configKey;
        this.permissionPrefix = permissionPrefix;
    }

    /** The section under {@code teleport-effects} that holds this category's entries. */
    public String configKey() {
        return configKey;
    }

    /**
     * The permission node that unlocks {@code id}.
     *
     * <p>Generated, never configurable. An operator who could rename the node would be able to
     * point two catalog entries at one permission, and a player who bought one would silently own
     * the other.
     */
    public String permission(String id) {
        return permissionPrefix + id;
    }

    /** The token stored in the database alongside the entry id. */
    public String storageKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Resolves a stored token, or null when the row predates this value or was hand-edited. */
    public static EffectCategory byStorageKey(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "animation" -> ANIMATION;
            case "particle" -> PARTICLE;
            default -> null;
        };
    }
}
