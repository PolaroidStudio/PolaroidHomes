package me.juancayc.polaroidhomes.effect;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import me.juancayc.polaroidhomes.config.EffectSettings;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Plays a declared Model Engine animation on an invisible marker spawned for the occasion.
 *
 * <p>A Model Engine model has to ride an entity, so every effect spawns one, plays, and removes it.
 * That entity is the hazard: if it is not removed it stays in the world forever, invisible,
 * accumulating one per teleport until somebody notices the entity count. Three things guard
 * against that, and all three are needed:
 *
 * <ol>
 *   <li>a scheduled removal on the declared duration, capped by {@code max-effect-seconds};</li>
 *   <li>a registry of live markers swept on {@link #shutdown()}, which covers a reload landing
 *       between spawn and removal;</li>
 *   <li>a persistent-data tag on the entity itself, so a marker that outlived even the registry
 *       (a crash, a chunk unloaded mid-effect) is still identifiable and can be removed the next
 *       time its chunk loads.</li>
 * </ol>
 */
public final class ModelEngineEffect implements TeleportEffect {

    /** Tags a spawned marker so it can be recognised after a restart, when the registry is gone. */
    public static final String MARKER_KEY = "effect_marker";

    private final Plugin plugin;
    private final NamespacedKey markerKey;
    private final double maxEffectSeconds;

    // Weak keys: if the entity is removed by anything else (a world unload, another plugin), the
    // entry can be collected rather than pinning a dead Entity object for the session.
    private final Set<Entity> liveMarkers =
            Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    // Model Engine keeps its OWN registry keyed on the entity, so removing the marker does not by
    // itself release the ModeledEntity. Holding the handle is what lets removal call destroy().
    private final Map<Entity, ModeledEntity> liveModels = new WeakHashMap<>();

    // A mistyped model id would otherwise log once per teleport, forever. Warn once per id.
    private final Set<String> warnedModels = ConcurrentHashMap.newKeySet();

    public ModelEngineEffect(Plugin plugin, double maxEffectSeconds) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, MARKER_KEY);
        this.maxEffectSeconds = maxEffectSeconds;
    }

    /** True when Model Engine is present and its API class actually resolved. */
    public static boolean isAvailable() {
        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            return false;
        }
        try {
            // Touching the API class is what proves the version on disk matches what was compiled
            // against. A present-but-incompatible Model Engine otherwise fails on the first
            // teleport instead of at startup.
            Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public NamespacedKey markerKey() {
        return markerKey;
    }

    @Override
    public void playEntry(Player player, EffectSettings settings) {
        play(player.getLocation(), settings);
    }

    @Override
    public void playArrival(Player player, Location destination, EffectSettings settings) {
        play(destination, settings);
    }

    private void play(Location location, EffectSettings settings) {
        if (!settings.hasModel() || location.getWorld() == null) {
            return;
        }
        try {
            ArmorStand marker = spawnMarker(location);
            ModeledEntity modeled = ModelEngineAPI.createModeledEntity(marker);
            ActiveModel model = ModelEngineAPI.createActiveModel(settings.model());
            if (modeled == null || model == null) {
                // An unknown model id is an operator typo, not an exception worth a stack trace.
                // The marker still has to go, or a typo leaks an entity per teleport.
                warnOnce(settings.model(), "no such model is loaded");
                discard(marker, modeled);
                return;
            }
            liveModels.put(marker, modeled);

            // addModel returns Optional<ActiveModel>, NOT void: an empty Optional means the model
            // was rejected and never mounted. Ignoring it is what turns a bad config into a silent
            // no-op, because the getAnimationHandler() call below would then run against a model
            // that is not attached to anything.
            Optional<ActiveModel> mounted = modeled.addModel(model, true);
            if (mounted.isEmpty()) {
                warnOnce(settings.model(), "Model Engine refused to mount it");
                discard(marker, modeled);
                return;
            }

            // playAnimation gives back an animation property, never a length. That is exactly why
            // the removal below is scheduled from the declared duration and not from the API.
            // A null property means the animation id is absent from an otherwise valid model —
            // worth saying out loud, but the marker still plays out its duration and is cleaned up.
            if (mounted.get().getAnimationHandler()
                    .playAnimation(settings.animation(), 0.0D, 0.0D, 1.0D, true) == null) {
                warnOnce(settings.model() + "#" + settings.animation(),
                        "the model has no animation by that name");
            }

            scheduleRemoval(marker, settings);
        } catch (Throwable ex) {
            // Model Engine's API moves between major versions. A broken integration must not break
            // the teleport itself, which has already been committed to by this point.
            plugin.getLogger().log(Level.WARNING, "Model Engine effect failed", ex);
        }
    }

    private ArmorStand spawnMarker(Location location) {
        ArmorStand marker = location.getWorld().spawn(location, ArmorStand.class, spawned -> {
            spawned.setInvisible(true);
            spawned.setMarker(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setPersistent(false);
            spawned.setCollidable(false);
            // Survives a restart in a way the in-memory registry cannot, so an orphan left by a
            // crash is still recognisable as ours.
            spawned.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        });
        liveMarkers.add(marker);
        return marker;
    }

    private void scheduleRemoval(ArmorStand marker, EffectSettings settings) {
        // Capped: a mistyped duration of 9999 would otherwise hold the marker for hours.
        double seconds = Math.min(Math.max(settings.durationSeconds(), 0.5D), maxEffectSeconds);
        long ticks = (long) Math.ceil(seconds * 20.0D);
        // Region scheduler rather than the global one: removing an entity is region-thread work,
        // which is what keeps folia-supported honest.
        marker.getScheduler().runDelayed(plugin, task -> removeMarker(marker), () -> {
        }, ticks);
    }

    private void removeMarker(Entity marker) {
        liveMarkers.remove(marker);
        // Destroy before removing the entity: Model Engine's registry is keyed on it, and a
        // ModeledEntity left behind keeps rendering bones for viewers who were already tracking it.
        ModeledEntity modeled = liveModels.remove(marker);
        if (modeled != null) {
            try {
                modeled.destroy();
            } catch (Throwable ignored) {
                // Already torn down by Model Engine itself; the entity removal below still applies.
            }
        }
        if (marker.isValid()) {
            marker.remove();
        }
    }

    /** Tears down a half-built effect: used on every early return out of the play path. */
    private void discard(Entity marker, ModeledEntity modeled) {
        if (modeled != null) {
            liveModels.put(marker, modeled);
        }
        removeMarker(marker);
    }

    /**
     * Logs a misconfigured model or animation once per id.
     *
     * <p>Without this the play path fails silently: the broad catch below swallows the NPE a
     * detached model would throw, so an operator sees no effect and no error at all.
     */
    private void warnOnce(String id, String reason) {
        if (warnedModels.add(id)) {
            plugin.getLogger().warning(
                    "Teleport effect '" + id + "' did not play: " + reason
                            + ". Check the model and animation names in config.yml.");
        }
    }

    /** Removes every marker this instance still owns. */
    @Override
    public void shutdown() {
        for (Entity marker : Set.copyOf(liveMarkers)) {
            removeMarker(marker);
        }
        liveMarkers.clear();
        liveModels.clear();
        warnedModels.clear();
    }

    /** True when the entity carries this plugin's marker tag but is not one we are still playing. */
    public boolean isOrphanMarker(Entity entity) {
        return entity.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE)
                && !liveMarkers.contains(entity);
    }
}
