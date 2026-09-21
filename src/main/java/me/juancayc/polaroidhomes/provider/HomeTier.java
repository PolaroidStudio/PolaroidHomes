package me.juancayc.polaroidhomes.provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rank that grants a home limit: the group's name and the number of homes it allows.
 *
 * <p>Tiers are a provider capability, not a universal one. EssentialsX enumerates them from its
 * {@code sethome-multiple} section; HuskHomes has no equivalent concept and reports none. A
 * provider that cannot enumerate tiers returns an empty list, and the menu answers "which rank
 * unlocks this slot?" with a generic locked label rather than inventing a rank name.
 *
 * <p>Bukkit-free on purpose, so the ordering and the locked-slot attribution can be unit tested.
 * Getting the attribution wrong means telling a paying player they already have a perk they do not.
 */
public record HomeTier(String group, int limit) {

    /**
     * Sorts tiers ascending by limit, so the first tier whose limit covers a slot index is the
     * cheapest rank that unlocks it.
     *
     * <p>Ties are broken by name to keep the result stable: a YAML map iterates in insertion order
     * on one server and, after an operator reshuffles the file, in another. A menu that names a
     * different rank for the same slot between restarts reads as a bug to players.
     */
    public static List<HomeTier> sorted(Map<String, Integer> configured) {
        List<HomeTier> tiers = new ArrayList<>(configured.size());
        configured.forEach((group, limit) -> {
            if (group != null && limit != null) {
                tiers.add(new HomeTier(group, limit));
            }
        });
        tiers.sort(Comparator.comparingInt(HomeTier::limit).thenComparing(HomeTier::group));
        return tiers;
    }

    /** The highest limit any configured tier grants, or {@code fallback} when none is configured. */
    public static int highestLimit(List<HomeTier> tiers, int fallback) {
        int highest = fallback;
        for (HomeTier tier : tiers) {
            highest = Math.max(highest, tier.limit());
        }
        return highest;
    }

    /**
     * The cheapest tier that would unlock the zero-based slot index, or null when no configured
     * tier reaches that far.
     */
    public static HomeTier unlockingSlot(List<HomeTier> tiers, int slotIndex) {
        int needed = slotIndex + 1;
        for (HomeTier tier : tiers) {
            if (tier.limit() >= needed) {
                return tier;
            }
        }
        return null;
    }

    /** Preserves the order tiers were read in, which keeps the sort deterministic. */
    public static Map<String, Integer> emptyOrderedMap() {
        return new LinkedHashMap<>();
    }
}
