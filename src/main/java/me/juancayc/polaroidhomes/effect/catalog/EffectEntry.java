package me.juancayc.polaroidhomes.effect.catalog;

import me.juancayc.polaroidhomes.config.EffectSettings;

/**
 * One purchasable effect: a departure and an arrival, sold as one thing.
 *
 * <p>The pairing is not a convenience. A Model Engine teleport effect is authored as a pair — a
 * {@code teleport_charge_origin} that builds up and a {@code teleport_charge_destination} that
 * resolves — and letting a player equip one effect's departure with another's arrival would produce
 * a teleport that starts as one thing and lands as another. It is also harder to sell: a shop entry
 * has to name one product, not two halves that may or may not go together. Particles follow the
 * same shape for the same reason, even though nothing technical forces it there.
 *
 * @param id          the catalog key, which is also what the permission node is generated from
 * @param category    which half of the catalog this came from; it decides the renderer
 * @param displayName MiniMessage name shown on the menu button
 * @param icon        item reference for the menu button, resolved through the item layer
 * @param entry       played where the player stands, before the teleport
 * @param arrival     played at the destination, after the player lands
 */
public record EffectEntry(String id,
                          EffectCategory category,
                          String displayName,
                          String icon,
                          EffectSettings entry,
                          EffectSettings arrival) {

    /** The node a player must hold for this effect to play. Generated from the id, never written. */
    public String permission() {
        return category.permission(id);
    }

    /**
     * Whether the server can actually render this entry right now.
     *
     * <p>An animation entry on a server with no Model Engine is unavailable: it is hidden from the
     * menu and never plays. The alternative — keeping it visible and having it play nothing — is
     * worse in the one way that matters for a catalog an operator sells. A player who bought a
     * visible, equippable effect and then sees nothing on every teleport has no way to tell a
     * missing dependency from a bug, and files a ticket. Hiding it makes the shortfall the
     * operator's to notice, which is why it is also announced in the console at enable.
     *
     * @param modelEngineAvailable whether Model Engine answered the availability check at enable
     */
    public boolean isAvailable(boolean modelEngineAvailable) {
        return category != EffectCategory.ANIMATION || modelEngineAvailable;
    }
}
