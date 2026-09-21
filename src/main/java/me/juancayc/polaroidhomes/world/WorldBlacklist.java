package me.juancayc.polaroidhomes.world;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The set of worlds homes may not exist in.
 *
 * <p>Bukkit-free on purpose. The rule an operator gets wrong is a case mismatch or a world that was
 * deleted from disk but left in the list, and both of those have to be provable in a unit test
 * rather than discovered by a player who cannot teleport home.
 *
 * <p>Matching is case-insensitive because a world folder is named by the filesystem, not by this
 * plugin: an operator who writes {@code Nether} for a world Bukkit calls {@code world_nether} has
 * made a typo, but one who writes {@code WORLD_NETHER} has not, and only the second is this class's
 * problem to absorb.
 *
 * <p>A name that matches no loaded world is kept rather than rejected. An operator may blacklist a
 * world before creating it, or leave an entry behind after deleting one; in both cases the entry is
 * simply inert, and dropping it would silently un-blacklist the world the day it comes back.
 */
public final class WorldBlacklist {

    private final Set<String> blocked;

    private WorldBlacklist(Set<String> blocked) {
        this.blocked = blocked;
    }

    /** An empty blacklist: every world is allowed. */
    public static WorldBlacklist empty() {
        return new WorldBlacklist(Set.of());
    }

    /**
     * Builds a blacklist from the raw config list.
     *
     * <p>Blank and null entries are dropped rather than stored: a YAML list with a stray {@code - ""}
     * would otherwise hold an entry that can never match a real world name but still makes
     * {@link #isEmpty()} lie about whether the feature is on.
     */
    public static WorldBlacklist of(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return empty();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                normalized.add(normalize(name));
            }
        }
        return new WorldBlacklist(Set.copyOf(normalized));
    }

    /**
     * True when a home in this world is blocked.
     *
     * <p>A null or blank world name answers false. That is the home whose world is not loaded, which
     * the menu already draws without coordinates: refusing it here would block a home for a reason
     * the operator never configured.
     */
    public boolean isBlocked(String worldName) {
        return worldName != null && !worldName.isBlank() && blocked.contains(normalize(worldName));
    }

    public boolean isEmpty() {
        return blocked.isEmpty();
    }

    /** The normalized entries, for a console line an operator has to act on. */
    public Set<String> names() {
        return blocked;
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
