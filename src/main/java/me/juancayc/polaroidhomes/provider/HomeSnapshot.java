package me.juancayc.polaroidhomes.provider;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Everything the homes menu needs about one player, read once before the window opens.
 *
 * <p>This type exists because the two providers disagree about threading. EssentialsX answers every
 * question synchronously; HuskHomes returns a {@code CompletableFuture} and its own documentation
 * forbids {@code join()} on it, because the future is completed by HuskHomes' own executor and
 * blocking the server thread on it deadlocks. Rendering an inventory is main-thread work that
 * cannot wait, so the menu is never given the provider at all — it is given this, already resolved.
 *
 * <p>The snapshot is immutable and is taken per window open, which is also why a home the player
 * creates while the menu is open does not appear until they reopen it. That is the honest trade: the
 * alternative is a render path that either blocks or draws half the data.
 *
 * @param homes     home names in the order the provider stores them
 * @param limit     how many homes this player may have
 * @param tiers     configured ranks ascending by limit, empty when the provider has no tier concept
 * @param locations each home's location, absent for a home whose world is not loaded
 */
public record HomeSnapshot(List<String> homes,
                           int limit,
                           List<HomeTier> tiers,
                           Map<String, Location> locations) {

    public HomeSnapshot(List<String> homes, int limit, List<HomeTier> tiers,
                        Map<String, Location> locations) {
        this.homes = List.copyOf(homes);
        this.limit = limit;
        this.tiers = List.copyOf(tiers);
        this.locations = Map.copyOf(locations);
    }

    /** What the menu shows for a player the provider could not answer for at all. */
    public static HomeSnapshot empty() {
        return new HomeSnapshot(List.of(), 0, List.of(), Map.of());
    }

    public @Nullable Location location(String home) {
        return locations.get(home);
    }

    /**
     * The grid size: the largest limit any configured rank grants, falling back to this player's own
     * limit.
     *
     * <p>The fallback is what keeps a tier-less provider usable. With no tiers there is nothing to
     * show above the player's own allowance, so the grid is sized to exactly what they have.
     */
    public int highestLimit() {
        return HomeTier.highestLimit(tiers, limit);
    }
}
