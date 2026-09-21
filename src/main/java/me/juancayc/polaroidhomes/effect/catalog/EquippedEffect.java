package me.juancayc.polaroidhomes.effect.catalog;

import org.jetbrains.annotations.Nullable;

/**
 * What one player has equipped, as it is stored.
 *
 * <p>A category and an id rather than a resolved {@link EffectEntry}, because the stored choice has
 * to outlive the catalog. An operator who removes an entry, renames it, or reloads with a typo
 * still has rows pointing at it, and a row that could only exist as a resolved entry would have to
 * be deleted on every such edit.
 */
public record EquippedEffect(EffectCategory category, String id) {

    /** Builds a choice from stored strings, or null when the row does not name a usable one. */
    public static @Nullable EquippedEffect of(@Nullable String category, @Nullable String id) {
        EffectCategory resolved = EffectCategory.byStorageKey(category);
        String normalized = EffectCatalog.normalizeId(id);
        return resolved == null || normalized == null
                ? null
                : new EquippedEffect(resolved, normalized);
    }
}
