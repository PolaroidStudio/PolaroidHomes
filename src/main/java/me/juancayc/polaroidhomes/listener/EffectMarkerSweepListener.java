package me.juancayc.polaroidhomes.listener;

import me.juancayc.polaroidhomes.effect.ModelEngineEffect;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Removes effect markers that survived the plugin that spawned them.
 *
 * <p>The scheduled removal and the in-memory registry both die with the server process. A marker
 * spawned in a chunk that unloaded mid-animation, or left behind by a crash, is not covered by
 * either, so it is removed the next time its chunk loads. Without this sweep those entities
 * accumulate invisibly, one per interrupted teleport, until somebody wonders why a region has
 * three thousand armour stands in it.
 */
public final class EffectMarkerSweepListener implements Listener {

    private final ModelEngineEffect effect;

    public EffectMarkerSweepListener(ModelEngineEffect effect) {
        this.effect = effect;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (effect.isOrphanMarker(entity)) {
                entity.remove();
            }
        }
    }
}
