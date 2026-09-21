package me.juancayc.polaroidhomes.effect;

import me.juancayc.polaroidhomes.config.EffectSettings;
import me.juancayc.polaroidhomes.config.PluginConfig;
import me.juancayc.polaroidhomes.effect.catalog.EffectCategory;
import me.juancayc.polaroidhomes.effect.catalog.EffectEntry;
import me.juancayc.polaroidhomes.effect.catalog.EffectSelection;
import me.juancayc.polaroidhomes.effect.catalog.SqlEffectStorage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Resolves the effect a given player has equipped and plays it.
 *
 * <p>This is what replaced the single global {@code TeleportEffect} the listeners used to hold. The
 * old shape had one implementation chosen at enable from {@code teleport-effects.mode}, so a
 * listener could ask it to play and be done. Now the renderer depends on whose teleport it is: two
 * players in the same teleport chain can be wearing different categories, and a third may be
 * wearing nothing at all.
 *
 * <p>Both renderers are held for the plugin's lifetime rather than built per effect, because the
 * Model Engine one owns the registry of live marker entities that {@link #shutdown()} sweeps. One
 * renderer per play would mean one registry per play, and a reload would find none of them.
 */
public final class EffectPlayer {

    private final PluginConfig config;
    private final SqlEffectStorage storage;

    /** Null when Model Engine is absent, which is also what makes animation entries unavailable. */
    private final @Nullable ModelEngineEffect animations;

    private final ParticleEffect particles = new ParticleEffect();

    public EffectPlayer(Plugin plugin, PluginConfig config, SqlEffectStorage storage,
                        boolean modelEngineAvailable) {
        this.config = config;
        this.storage = storage;
        this.animations = modelEngineAvailable
                ? new ModelEngineEffect(plugin, config.maxEffectSeconds())
                : null;
    }

    /** True when animation entries can play on this server. */
    public boolean modelEngineAvailable() {
        return animations != null;
    }

    /**
     * The entries of one category the menu should list.
     *
     * <p>Filtered by availability, not by permission: a locked entry is still listed, because a
     * catalog a player cannot see is a catalog nobody buys from. An entry the server cannot render
     * is not listed at all, which is a different thing — see {@code EffectEntry#isAvailable}.
     */
    public List<EffectEntry> available(EffectCategory category) {
        return config.effectCatalog().available(category, modelEngineAvailable());
    }

    /**
     * The entry this player's teleport should be decorated with, or null for a clean teleport.
     *
     * <p>Null is the default and the common case on a fresh upgrade: no default effect exists, so a
     * player who has equipped nothing gets no particles, no model and no warmup stretch.
     */
    public @Nullable EffectEntry resolve(Player player) {
        return EffectSelection.resolve(config.effectCatalog(), storage.equipped(player.getUniqueId()),
                modelEngineAvailable(), player::hasPermission);
    }

    /**
     * Ticks the warmup should be held for so the departure finishes before the player moves.
     *
     * <p>Zero for a player with nothing equipped and zero for any particle entry, which is the
     * honest answer in both cases: there is nothing to wait for. Only an animation asks for time,
     * and only the EssentialsX path can grant it.
     */
    public long warmupTicks(@Nullable EffectEntry entry) {
        return entry == null ? 0L : entry.entry().durationTicks();
    }

    /** Plays the departure where the player stands. A null entry plays nothing. */
    public void playEntry(Player player, @Nullable EffectEntry entry) {
        if (entry == null) {
            return;
        }
        renderer(entry).playEntry(player, entry.entry());
    }

    /** Plays the arrival at the destination. A null entry plays nothing. */
    public void playArrival(Player player, Location destination, @Nullable EffectEntry entry) {
        if (entry == null) {
            return;
        }
        renderer(entry).playArrival(player, destination, entry.arrival());
    }

    /**
     * The renderer for an entry's category.
     *
     * <p>An animation entry can only reach here when {@link #animations} exists: the resolve path
     * drops unavailable entries, and the menu never offers one. The null check is still made rather
     * than asserted, because the cost of being wrong is a NullPointerException thrown inside a
     * teleport the server has already committed to.
     */
    private TeleportEffect renderer(EffectEntry entry) {
        return switch (entry.category()) {
            case ANIMATION -> animations == null ? NOTHING : animations;
            case PARTICLE -> particles;
        };
    }

    /** Removes everything the renderers still own. Called on disable and on reload. */
    public void shutdown() {
        if (animations != null) {
            animations.shutdown();
        }
        particles.shutdown();
    }

    /** The Model Engine renderer, for the sweep listener that removes orphaned markers. */
    public @Nullable ModelEngineEffect animations() {
        return animations;
    }

    /**
     * A renderer that draws nothing.
     *
     * <p>Only reachable if an animation entry survived the availability checks on a server with no
     * Model Engine, which is a bug rather than a configuration. Drawing nothing keeps the teleport
     * working while that bug is found.
     */
    private static final TeleportEffect NOTHING = new TeleportEffect() {

        @Override
        public void playEntry(Player player, EffectSettings settings) {
            // Intentionally empty.
        }

        @Override
        public void playArrival(Player player, Location destination, EffectSettings settings) {
            // Intentionally empty.
        }

        @Override
        public void shutdown() {
            // Nothing to own, nothing to release.
        }
    };
}
