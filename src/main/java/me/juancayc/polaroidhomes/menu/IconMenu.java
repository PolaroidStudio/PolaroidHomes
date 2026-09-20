package me.juancayc.polaroidhomes.menu;

import me.juancayc.polaroidhomes.text.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
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
 */
public final class IconMenu extends MenuHolder {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int CHOICES_PER_PAGE = (ROWS - 1) * 9;

    private final MenuContext context;
    private final UUID targetId;
    private final String home;
    private final HomesMenu parent;
    private final List<String> available;

    private int page;

    public IconMenu(MenuContext context, Player viewer, UUID targetId, String home, HomesMenu parent) {
        this.context = context;
        this.targetId = targetId;
        this.home = home;
        this.parent = parent;
        this.available = resolveAvailable();
        setInventory(Bukkit.createInventory(this, SIZE,
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

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(available.size() / (double) CHOICES_PER_PAGE));
    }

    @Override
    public void render(Player viewer) {
        beginUpdate();
        try {
            Inventory inventory = getInventory();
            inventory.clear();
            clearHandlers();

            ItemStack filler = context.items().filler(context.config().fillerIcon());
            for (int slot = 0; slot < SIZE; slot++) {
                inventory.setItem(slot, filler.clone());
            }

            drawChoices(inventory);
            drawNavigation(inventory);
        } finally {
            endUpdate();
        }
    }

    private void drawChoices(Inventory inventory) {
        String current = context.icons().icon(targetId, home);
        int first = page * CHOICES_PER_PAGE;

        for (int offset = 0; offset < CHOICES_PER_PAGE; offset++) {
            int index = first + offset;
            if (index >= available.size()) {
                break;
            }
            String reference = available.get(index);
            boolean selected = Objects.equals(reference, current);

            inventory.setItem(offset, context.items().button(
                    reference,
                    selected ? "menu.icons.selected.name" : "menu.icons.choice.name",
                    selected ? "menu.icons.selected.lore" : "menu.icons.choice.lore",
                    Material.PAPER));

            if (selected) {
                // Already in use: clicking it would write the value it already has, so it does
                // nothing and stays silent.
                continue;
            }
            handlers().put(offset, (player, click) -> {
                context.playClick(player);
                context.icons().setIcon(targetId, home, reference);
                context.messages().send(player, "icons.changed",
                        "%home%", MessageService.escape(home));
                render(player);
            });
        }
    }

    private void drawNavigation(Inventory inventory) {
        int pages = pageCount();

        if (page > 0) {
            inventory.setItem(SIZE - 9, context.items().button(
                    "ARROW", "menu.homes.previous_page.name", "menu.homes.previous_page.lore",
                    Material.ARROW));
            handlers().put(SIZE - 9, (player, click) -> {
                context.playClick(player);
                page--;
                render(player);
            });
        }

        if (page < pages - 1) {
            inventory.setItem(SIZE - 2, context.items().button(
                    "ARROW", "menu.homes.next_page.name", "menu.homes.next_page.lore",
                    Material.ARROW));
            handlers().put(SIZE - 2, (player, click) -> {
                context.playClick(player);
                page++;
                render(player);
            });
        }

        inventory.setItem(SIZE - 6, context.items().button(
                "BARRIER", "menu.icons.reset.name", "menu.icons.reset.lore", Material.BARRIER));
        handlers().put(SIZE - 6, (player, click) -> {
            context.playClick(player);
            context.icons().setIcon(targetId, home, null);
            context.messages().send(player, "icons.reset", "%home%", MessageService.escape(home));
            render(player);
        });

        inventory.setItem(SIZE - 5, context.items().button(
                "ARROW", "menu.icons.back.name", "menu.icons.back.lore", Material.ARROW));
        handlers().put(SIZE - 5, (player, click) -> {
            context.playClick(player);
            // A fresh window rather than the stored parent: the parent was sized from a possibly
            // older configuration and its contents are stale after an icon change.
            context.registry().open(player,
                    new HomesMenu(context, player, Bukkit.getOfflinePlayer(targetId),
                            parent.teleportAction()));
        });
    }
}
