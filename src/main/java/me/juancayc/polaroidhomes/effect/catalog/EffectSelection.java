package me.juancayc.polaroidhomes.effect.catalog;

import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The rules that turn a stored choice plus a permission check into the effect that actually plays.
 *
 * <p>Deliberately Bukkit-free, and the permission check arrives as a predicate over node strings,
 * because every rule here has a failure mode a server would only reveal by accident. "Losing a rank
 * silently keeps the row" is invisible until somebody's subscription lapses; "equipping a particle
 * unequips an animation" is invisible until a player owns one of each. Both run in a unit test on
 * every build instead.
 *
 * <h2>One equipped thing, across both categories</h2>
 *
 * <p>Not one per category. A player has exactly one departure and one arrival, so two equipped
 * effects would have to be ordered somehow, and any ordering is a rule a player has to learn before
 * they can predict what their own teleport looks like. Equipping anything therefore replaces
 * whatever was equipped, whichever category it came from.
 *
 * <h2>A lost permission is kept, not cleared</h2>
 *
 * <p>The stored row survives the permission going away: the effect stops playing and nothing else
 * happens. The alternative is deleting the row the moment a rank lapses, which turns a renewed rank
 * into a support request — the player paid twice and has to re-equip something they never
 * unequipped. Keeping it costs one row and makes a renewal invisible, which is what it should be.
 */
public final class EffectSelection {

    private EffectSelection() {
    }

    /**
     * The entry a player's stored choice resolves to, or null when nothing should play.
     *
     * <p>Null covers four different situations on purpose, because the teleport path treats them
     * identically: nothing was ever equipped, the entry was removed from the catalog, the entry
     * needs Model Engine and the server has none, or the player no longer holds the permission.
     * Only the menu needs to tell them apart, and it asks separately.
     *
     * @param modelEngineAvailable whether Model Engine answered the availability check at enable
     * @param hasPermission        resolves a permission node for the player in question
     */
    public static @Nullable EffectEntry resolve(EffectCatalog catalog,
                                                @Nullable EquippedEffect equipped,
                                                boolean modelEngineAvailable,
                                                Predicate<String> hasPermission) {
        if (equipped == null) {
            return null;
        }
        EffectEntry entry = catalog.find(equipped.category(), equipped.id());
        if (entry == null || !entry.isAvailable(modelEngineAvailable)) {
            return null;
        }
        return hasPermission.test(entry.permission()) ? entry : null;
    }

    /**
     * Whether a player may equip an entry right now.
     *
     * <p>Availability is checked as well as the permission: an animation entry a player owns on a
     * server whose Model Engine was uninstalled is not equippable, because equipping it would
     * replace a working effect with one that cannot play.
     */
    public static boolean canEquip(EffectEntry entry,
                                   boolean modelEngineAvailable,
                                   Predicate<String> hasPermission) {
        return entry.isAvailable(modelEngineAvailable) && hasPermission.test(entry.permission());
    }

    /**
     * The choice that results from clicking {@code entry} while {@code equipped} is worn.
     *
     * <p>Clicking the equipped entry unequips it, so the menu needs no separate "take this off"
     * button next to every row — though an explicit one exists too, because a player who wants
     * nothing should not have to remember which entry they are wearing to get there.
     *
     * @return the new choice, or null meaning nothing equipped
     */
    public static @Nullable EquippedEffect toggle(@Nullable EquippedEffect equipped,
                                                  EffectEntry entry) {
        EquippedEffect clicked = new EquippedEffect(entry.category(), entry.id());
        return clicked.equals(equipped) ? null : clicked;
    }

    /** True when {@code equipped} names exactly this entry, whatever its permission says. */
    public static boolean isEquipped(@Nullable EquippedEffect equipped, EffectEntry entry) {
        return equipped != null
                && equipped.category() == entry.category()
                && equipped.id().equals(entry.id());
    }
}
