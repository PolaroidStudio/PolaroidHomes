package me.juancayc.polaroidhomes.menu;

import me.juancayc.polaroidhomes.text.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The icon picker for one home.
 *
 * <p>Like the homes grid, every slot here is chrome. A choice the player clicks is not taken from
 * the window: the reference string behind it is written to the icon store and the window is
 * rebuilt, so nothing ever leaves this inventory.
 *
 * <p>Choices whose plugin is missing, or whose id no longer exists, are dropped while the page is
 * being built rather than drawn as a broken slot. A picker showing an item the server cannot make
 * is worse than a shorter picker.
 *
 * <p>Its layout comes from the {@code icons} section of menu.yml, through the same validated
 * template the homes grid uses, so the two screens cannot drift apart in how they are configured.
 */
public final class IconMenu extends MenuHolder {

    private final MenuContext context;
    private final UUID targetId;
    private final String home;
    private final HomesMenu parent;
    private final List<String> available;
    private final MenuTemplate template;

    private int page;

    public IconMenu(MenuContext context, Player viewer, UUID targetId, String home, HomesMenu parent) {
        this.context = context;
        this.targetId = targetId;
        this.home = home;
        this.parent = parent;
        this.available = resolveAvailable();
        this.template = context.menus().icons();
        setInventory(Bukkit.createInventory(this, template.inventorySize(),
                context.messages().build("menu.icons.title", "%home%", MessageService.escape(home))));
    }

    /**
     * Resolves every configured choice once, at open, and keeps only the ones that produced an
     * item. Resolving per render would repeat the same plugin lookups on every page flip.
     */
    private List<String> resolveAvailable() {
        List<String> resolved = new ArrayList<>();
        for (String reference : context.config().iconChoices()) {
            if (reference != null && !reference.isBlank() && context.resolves(reference)) {
                resolved.add(reference);
            }
        }
        return resolved;
    }

    private int choicesPerPage() {
        return template.slotsPerPage();
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(available.size() / (double) choicesPerPage()));
    }

    @Override
    public void render(Player viewer) {
        beginUpdate();
        try {
            Inventory inventory = getInventory();
            inventory.clear();
            clearHandlers();

            drawChrome(inventory);
            drawChoices(inventory);
        } finally {
            endUpdate();
        }
    }

    private void drawChrome(Inventory inventory) {
        int pages = pageCount();

        for (Map.Entry<Integer, MenuElement> entry : template.slots().entrySet()) {
            int slot = entry.getKey();
            MenuElement element = entry.getValue();
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
                case ICON_RESET -> {
                    inventory.setItem(slot, context.items().element(definition, "BARRIER",
                            "menu.icons.reset.name", "menu.icons.reset.lore", Material.BARRIER));
                    handlers().put(slot, (player, click) -> {
                        context.playClick(player);
                        context.icons().setIcon(targetId, home, null);
                        context.messages().send(player, "icons.reset",
                                "%home%", MessageService.escape(home));
                        render(player);
                    });
                }
                case ICON_BACK -> {
                    inventory.setItem(slot, context.items().element(definition, "ARROW",
                            "menu.icons.back.name", "menu.icons.back.lore", Material.ARROW));
                    handlers().put(slot, (player, click) -> {
                        context.playClick(player);
                        // A fresh window rather than the stored parent: the parent was sized from a
                        // possibly older configuration and its contents are stale after an icon
                        // change.
                        context.registry().open(player,
                                new HomesMenu(context, player, Bukkit.getOfflinePlayer(targetId),
                                        parent.teleportAction()));
                    });
                }
                default -> {
                }
            }
        }
    }

    private ItemStack filler(MenuTemplate.ElementDefinition definition) {
        String reference = definition == null || definition.item().isBlank()
                ? "BLACK_STAINED_GLASS_PANE"
                : definition.item();
        return context.items().filler(reference);
    }

    private void drawChoices(Inventory inventory) {
        String current = context.icons().icon(targetId, home);
        List<Integer> positions = template.homeSlots();
        int first = page * choicesPerPage();

        for (int offset = 0; offset < positions.size(); offset++) {
            int index = first + offset;
            if (index >= available.size()) {
                // Past the end of the list: the slot keeps whatever the template drew there, which
                // for a blank character is nothing at all.
                break;
            }
            int slot = positions.get(offset);
            String reference = available.get(index);
            boolean selected = Objects.equals(reference, current);

            inventory.setItem(slot, context.items().button(
                    reference,
                    selected ? "menu.icons.selected.name" : "menu.icons.choice.name",
                    selected ? "menu.icons.selected.lore" : "menu.icons.choice.lore",
                    Material.PAPER));

            if (selected) {
                // Already in use: clicking it would write the value it already has, so it does
                // nothing and stays silent.
                continue;
            }
            handlers().put(slot, (player, click) -> {
                context.playClick(player);
                context.icons().setIcon(targetId, home, reference);
                context.messages().send(player, "icons.changed",
                        "%home%", MessageService.escape(home));
                render(player);
            });
        }
    }
}
