package me.juancayc.polaroidhomes.menu;

import me.juancayc.polaroidhomes.effect.catalog.EffectCategory;
import me.juancayc.polaroidhomes.effect.catalog.EffectEntry;
import me.juancayc.polaroidhomes.effect.catalog.EquippedEffect;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The effect catalog's front door: one button per category.
 *
 * <p>Every slot is chrome, exactly as in the homes grid and the icon picker. Nothing here is an
 * item the player owns, so every click is cancelled and dispatched as an action instead.
 *
 * <h2>Why a picker rather than one window with both sections</h2>
 *
 * <p>A single window holding both catalogs would be the nicer screen right up until an operator
 * adds their fifth animation. The catalogs are a price list an operator grows over time, so their
 * size is not something this plugin can bound, and a combined window either has to page both
 * sections together — which pages one category's entries onto a screen whose other half belongs to
 * the other — or reserve fixed rows for each, which wastes half the window when one category is
 * empty and truncates the other when it is not.
 *
 * <p>A picker also answers the thing a player actually came to do. They want a specific look, and
 * "animation or particles" is the first distinction they would make anyway. It costs one extra
 * click and it makes each list a plain paged grid with one job, which is the shape the icon picker
 * already proved works here.
 *
 * <p>Both category buttons are always drawn, including for a category that is empty or whose
 * entries the player owns none of: a button that disappears when there is nothing behind it reads
 * as a broken menu, while one that opens an empty list reads as a catalog with nothing in it yet.
 * The lore says which of those it is.
 */
public final class EffectPickerMenu extends MenuHolder {

    private final MenuContext context;
    private final HomesMenu parent;
    private final MenuTemplate template;

    public EffectPickerMenu(MenuContext context, HomesMenu parent) {
        this.context = context;
        this.parent = parent;
        this.template = context.menus().effectPicker();
        setInventory(Bukkit.createInventory(this, template.inventorySize(),
                context.messages().build("menu.effects.title")));
    }

    @Override
    public void render(Player viewer) {
        beginUpdate();
        try {
            Inventory inventory = getInventory();
            inventory.clear();
            clearHandlers();
            draw(viewer, inventory);
        } finally {
            // In a finally block for the same reason the other menus do it: a render that throws
            // halfway would otherwise leave the latch up and drop every later click silently.
            endUpdate();
        }
    }

    private void draw(Player viewer, Inventory inventory) {
        EquippedEffect equipped = context.effects().equipped(viewer.getUniqueId());

        for (Map.Entry<Integer, MenuElement> slotEntry : template.slots().entrySet()) {
            int slot = slotEntry.getKey();
            MenuElement element = slotEntry.getValue();
            MenuTemplate.ElementDefinition definition = template.definition(element);

            switch (element) {
                case FILLER -> inventory.setItem(slot, filler(definition));
                case EFFECT_ANIMATIONS -> drawCategory(viewer, inventory, slot, definition,
                        EffectCategory.ANIMATION, "menu.effects.animations");
                case EFFECT_PARTICLES -> drawCategory(viewer, inventory, slot, definition,
                        EffectCategory.PARTICLE, "menu.effects.particles");
                case EFFECT_NONE -> drawUnequip(inventory, slot, definition, equipped);
                case EFFECT_BACK -> drawBack(inventory, slot, definition);
                default -> {
                }
            }
        }
    }

    /**
     * Draws one category button, counting what the viewer owns.
     *
     * <p>The count is of entries the player can actually equip, not of the catalog, because that is
     * the number they came to see. The total is shown beside it so a player who owns none still
     * learns there is something to buy.
     */
    private void drawCategory(Player viewer, Inventory inventory, int slot,
                              MenuTemplate.ElementDefinition definition,
                              EffectCategory category, String key) {
        List<EffectEntry> available =
                context.effectPlayer().available(category);
        int unlocked = 0;
        for (EffectEntry entry : available) {
            if (viewer.hasPermission(entry.permission())) {
                unlocked++;
            }
        }

        inventory.setItem(slot, context.items().element(definition, "NETHER_STAR",
                key + ".name", key + ".lore", Material.NETHER_STAR,
                "%unlocked%", String.valueOf(unlocked),
                "%total%", String.valueOf(available.size())));

        handlers().put(slot, (player, click) -> {
            context.playClick(player);
            context.registry().open(player,
                    new EffectListMenu(context, player, category, parent));
        });
    }

    /**
     * Draws the explicit unequip button.
     *
     * <p>It exists alongside clicking the equipped entry to take it off, because a player who wants
     * a clean teleport should not have to remember which of two lists their current effect is in
     * to get there. When nothing is equipped it is drawn as already-current and carries no handler,
     * so the button never fires an action that does nothing.
     */
    private void drawUnequip(Inventory inventory, int slot,
                             MenuTemplate.ElementDefinition definition, EquippedEffect equipped) {
        boolean current = equipped == null;
        String key = current ? "menu.effects.none_current" : "menu.effects.none";
        inventory.setItem(slot, context.items().element(definition, "BARRIER",
                key + ".name", key + ".lore", Material.BARRIER));
        if (current) {
            return;
        }
        handlers().put(slot, (player, click) -> {
            context.playClick(player);
            context.effects().equip(player.getUniqueId(), null);
            context.messages().send(player, "effects.unequipped");
            render(player);
        });
    }

    private void drawBack(Inventory inventory, int slot,
                          MenuTemplate.ElementDefinition definition) {
        inventory.setItem(slot, context.items().element(definition, "ARROW",
                "menu.effects.back.name", "menu.effects.back.lore", Material.ARROW));
        handlers().put(slot, (player, click) -> {
            context.playClick(player);
            // A fresh window rather than the stored parent, exactly as the icon picker does: the
            // parent was sized from a possibly older configuration and reusing it would reopen a
            // window whose layout no longer matches the file.
            context.registry().open(player,
                    new HomesMenu(context, player, Bukkit.getOfflinePlayer(player.getUniqueId()),
                            parent.snapshot(), parent.teleportAction()));
        });
    }

    private ItemStack filler(MenuTemplate.ElementDefinition definition) {
        String reference = definition == null || definition.item().isBlank()
                ? "BLACK_STAINED_GLASS_PANE"
                : definition.item();
        return context.items().filler(reference);
    }
}
