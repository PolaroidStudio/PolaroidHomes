package me.juancayc.polaroidhomes.menu;

import me.juancayc.polaroidhomes.effect.catalog.EffectCategory;
import me.juancayc.polaroidhomes.effect.catalog.EffectEntry;
import me.juancayc.polaroidhomes.effect.catalog.EffectSelection;
import me.juancayc.polaroidhomes.effect.catalog.EquippedEffect;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * One category of the effect catalog, as a paged grid.
 *
 * <p>Every slot is chrome, like every other window in this plugin: clicking an entry writes a row
 * to this plugin's own store and rebuilds the window, so nothing ever leaves the inventory.
 *
 * <h2>The three shapes an entry takes</h2>
 *
 * <ul>
 *   <li><b>Equipped</b> — glinting, and clicking it takes it off.</li>
 *   <li><b>Unlocked</b> — the entry's own icon and name, and clicking it equips it.</li>
 *   <li><b>Locked</b> — a padlock, and no handler at all.</li>
 * </ul>
 *
 * <p>A locked entry says it is locked and nothing more. This plugin knows only whether a permission
 * is held; it has no idea what the node costs, where it is sold, or whether it is sold at all
 * rather than granted with a rank. Printing a price it inferred would eventually print the wrong
 * one, and a menu that quotes a stale price is worse than one that quotes none.
 *
 * <p>Locked entries are shown rather than hidden, which is the same judgement the homes grid makes
 * about slots above a player's rank: a catalog a player cannot see is a catalog nobody buys from.
 * An <em>unavailable</em> entry is different and is not here at all — see
 * {@code EffectEntry#isAvailable}.
 */
public final class EffectListMenu extends MenuHolder {

    private final MenuContext context;
    private final EffectCategory category;
    private final HomesMenu grid;
    private final MenuTemplate template;
    private final List<EffectEntry> entries;

    private int page;

    public EffectListMenu(MenuContext context, Player viewer, EffectCategory category,
                          HomesMenu grid) {
        this.context = context;
        this.category = category;
        this.grid = grid;
        this.template = context.menus().effectList();
        // Resolved once, at open, rather than per render: the catalog cannot change while a window
        // is up, because a reload closes every open window first.
        this.entries = context.effectPlayer().available(category);
        setInventory(Bukkit.createInventory(this, template.inventorySize(),
                context.messages().build("menu.effects." + titleKey() + ".title")));
    }

    private String titleKey() {
        return category == EffectCategory.ANIMATION ? "animations" : "particles";
    }

    private int perPage() {
        return template.slotsPerPage();
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(entries.size() / (double) perPage()));
    }

    @Override
    public void render(Player viewer) {
        beginUpdate();
        try {
            Inventory inventory = getInventory();
            inventory.clear();
            clearHandlers();
            drawChrome(viewer, inventory);
            drawEntries(viewer, inventory);
        } finally {
            endUpdate();
        }
    }

    private void drawChrome(Player viewer, Inventory inventory) {
        int pages = pageCount();
        EquippedEffect equipped = context.effects().equipped(viewer.getUniqueId());

        for (Map.Entry<Integer, MenuElement> slotEntry : template.slots().entrySet()) {
            int slot = slotEntry.getKey();
            MenuElement element = slotEntry.getValue();
            MenuTemplate.ElementDefinition definition = template.definition(element);

            switch (element) {
                case FILLER -> inventory.setItem(slot, filler(definition));
                case PREVIOUS_PAGE -> {
                    if (page > 0) {
                        inventory.setItem(slot, context.items().element(definition, "ARROW",
                                "menu.homes.previous_page.name", "menu.homes.previous_page.lore",
                                Material.ARROW));
                        handlers().put(slot, (player, click) -> {
                            context.playClick(player);
                            page--;
                            render(player);
                        });
                    } else {
                        // Nowhere to go back to, so the slot reads as background rather than as a
                        // button that refuses the click.
                        inventory.setItem(slot, filler(template.definition(MenuElement.FILLER)));
                    }
                }
                case NEXT_PAGE -> {
                    if (page < pages - 1) {
                        inventory.setItem(slot, context.items().element(definition, "ARROW",
                                "menu.homes.next_page.name", "menu.homes.next_page.lore",
                                Material.ARROW));
                        handlers().put(slot, (player, click) -> {
                            context.playClick(player);
                            page++;
                            render(player);
                        });
                    } else {
                        inventory.setItem(slot, filler(template.definition(MenuElement.FILLER)));
                    }
                }
                case EFFECT_NONE -> drawUnequip(inventory, slot, definition, equipped);
                case EFFECT_BACK -> {
                    inventory.setItem(slot, context.items().element(definition, "ARROW",
                            "menu.effects.back.name", "menu.effects.back.lore", Material.ARROW));
                    handlers().put(slot, (player, click) -> {
                        context.playClick(player);
                        // Back to the picker, not to the grid: this window was opened from there,
                        // and a player comparing the two categories should not have to walk the
                        // whole way out and back in.
                        context.registry().open(player,
                                new EffectPickerMenu(context, grid));
                    });
                }
                default -> {
                }
            }
        }
    }

    private void drawUnequip(Inventory inventory, int slot,
                             MenuTemplate.ElementDefinition definition, EquippedEffect equipped) {
        boolean current = equipped == null;
        String key = current ? "menu.effects.none_current" : "menu.effects.none";
        inventory.setItem(slot, context.items().element(definition, "BARRIER",
                key + ".name", key + ".lore", Material.BARRIER));
        if (current) {
            // Already wearing nothing: the button would write the value it already has, so it does
            // nothing and stays silent.
            return;
        }
        handlers().put(slot, (player, click) -> {
            context.playClick(player);
            context.effects().equip(player.getUniqueId(), null);
            context.messages().send(player, "effects.unequipped");
            render(player);
        });
    }

    private void drawEntries(Player viewer, Inventory inventory) {
        EquippedEffect equipped = context.effects().equipped(viewer.getUniqueId());
        List<Integer> positions = template.homeSlots();
        int first = page * perPage();

        for (int offset = 0; offset < positions.size(); offset++) {
            int index = first + offset;
            if (index >= entries.size()) {
                // Past the end of the catalog: the slot keeps whatever the template drew there,
                // which for a blank character is nothing at all.
                break;
            }
            drawEntry(inventory, positions.get(offset), entries.get(index), equipped, viewer);
        }
    }

    private void drawEntry(Inventory inventory, int slot, EffectEntry entry,
                           EquippedEffect equipped, Player viewer) {
        boolean unlocked = viewer.hasPermission(entry.permission());
        boolean worn = EffectSelection.isEquipped(equipped, entry);

        // Three shapes for one button, chosen here rather than by editing one lore in place: an
        // equipped entry and a locked one are different things, not the same thing with different
        // words, and a player scanning the page has to tell them apart without reading.
        String key = worn ? "menu.effects.entry_equipped"
                : unlocked ? "menu.effects.entry" : "menu.effects.entry_locked";
        // The locked shape draws the configured padlock rather than the entry's own icon, so a
        // player can see at a glance which rows are theirs. The name is still shown: the whole
        // point of listing a locked entry is that they learn it exists.
        String reference = unlocked ? entry.icon() : context.config().lockedSlotIcon();

        ItemStack item = context.items().button(
                reference,
                key + ".name",
                key + ".lore",
                unlocked ? Material.NETHER_STAR : Material.IRON_BARS,
                // The display name comes from the operator's own config and may carry colour tags,
                // so it is substituted as written rather than escaped — escaping it would print an
                // operator's gradient as literal text.
                "%effect%", entry.displayName());
        if (worn) {
            item = context.items().glint(item);
        }
        inventory.setItem(slot, item);

        if (!unlocked) {
            // No handler: a locked entry does nothing, so it also stays silent. A click sound on a
            // button that refuses the click reads as a malfunction.
            return;
        }

        handlers().put(slot, (player, click) -> {
            context.playClick(player);
            // Re-checked at click time, not trusted from the render: a permission can lapse or be
            // revoked while the window is open, and a stale button must not equip something the
            // player no longer owns.
            if (!EffectSelection.canEquip(entry, context.effectPlayer().modelEngineAvailable(),
                    player::hasPermission)) {
                context.messages().send(player, "effects.locked",
                        "%effect%", entry.displayName());
                render(player);
                return;
            }
            EquippedEffect now = context.effects().equipped(player.getUniqueId());
            EquippedEffect next = EffectSelection.toggle(now, entry);
            context.effects().equip(player.getUniqueId(), next);
            if (next == null) {
                context.messages().send(player, "effects.unequipped");
            } else {
                context.messages().send(player, "effects.equipped",
                        "%effect%", entry.displayName());
            }
            render(player);
        });
    }

    private ItemStack filler(MenuTemplate.ElementDefinition definition) {
        String reference = definition == null || definition.item().isBlank()
                ? "BLACK_STAINED_GLASS_PANE"
                : definition.item();
        return context.items().filler(reference);
    }
}
