package me.juancayc.polaroidhomes.menu;

import me.juancayc.polaroidhomes.essentials.HomeTier;
import me.juancayc.polaroidhomes.text.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * The homes grid.
 *
 * <p>Slot classification, which decides which rules apply: <b>every slot is chrome</b>. The window
 * holds no item the player owns, so the correct behaviour is to cancel every click and drag and
 * dispatch an action instead. There is no persistence path to protect, which is what separates this
 * from a backpack or a vault.
 *
 * <p>The grid is sized at the server's maximum rather than at the viewer's own limit, so a player
 * can see what the ranks above them unlock. {@link GridLayout} caps that at
 * {@code gui.max-displayed-slots}, which is what keeps an "unlimited" EssentialsX tier from asking
 * for an inventory that cannot exist.
 */
public final class HomesMenu extends MenuHolder {

    private final MenuContext context;
    private final UUID targetId;
    private final String targetName;
    private final boolean ownWindow;
    private final HomeAction onTeleport;

    // Fixed for the lifetime of the window: the inventory is sized from it at construction, so a
    // mid-life change would produce a slot map the open window cannot hold. A configuration reload
    // takes effect on the next open, which is when a correctly sized window can be built.
    private final GridLayout layout;
    private int page;

    /** What the menu asks the plugin to do when a home is clicked. */
    @FunctionalInterface
    public interface HomeAction {
        void run(Player viewer, String home);
    }

    public HomesMenu(MenuContext context, Player viewer, OfflinePlayer target, HomeAction onTeleport) {
        this.context = context;
        this.targetId = target.getUniqueId();
        this.targetName = target.getName() == null ? targetId.toString() : target.getName();
        this.ownWindow = viewer.getUniqueId().equals(targetId);
        this.onTeleport = onTeleport;
        this.layout = computeLayout(viewer);
        setInventory(createInventory(viewer));
    }

    private GridLayout computeLayout(Player viewer) {
        List<HomeTier> tiers = context.essentials().tiers();
        // The fallback matters: with no tiers configured at all, the viewer's own resolved limit is
        // still a real number and still deserves a grid.
        int highest = HomeTier.highestLimit(tiers, context.essentials().homeLimit(viewer));
        return GridLayout.of(context.config().rows(), highest, context.config().maxDisplayedSlots());
    }

    private Inventory createInventory(Player viewer) {
        String titleKey = ownWindow ? "menu.homes.title" : "menu.homes.title_other";
        // Escaped: the target's name is player-controlled on servers that allow renames, and an
        // unescaped one could open a tag in the title.
        return Bukkit.createInventory(this, layout.inventorySize(),
                context.messages().build(titleKey, "%player%", MessageService.escape(targetName)));
    }

    @Override
    public void render(Player viewer) {
        beginUpdate();
        try {
            Inventory inventory = getInventory();
            inventory.clear();
            clearHandlers();

            ItemStack filler = context.items().filler(context.config().fillerIcon());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler.clone());
            }

            drawHomes(viewer, inventory);
            drawNavigation(viewer, inventory);
        } finally {
            // In a finally block on purpose: a render that throws halfway would otherwise leave the
            // latch up forever and every later click would be silently dropped.
            endUpdate();
        }
    }

    private void drawHomes(Player viewer, Inventory inventory) {
        Player target = ownWindow ? viewer : Bukkit.getPlayer(targetId);
        List<String> homes = target == null ? List.of() : context.essentials().homes(target);
        int limit = target == null ? 0 : context.essentials().homeLimit(target);
        List<HomeTier> tiers = context.essentials().tiers();

        int first = layout.firstIndexOfPage(page);
        int count = layout.slotsOnPage(page);

        for (int offset = 0; offset < count; offset++) {
            int index = first + offset;
            if (index < homes.size()) {
                drawHome(inventory, offset, target, homes.get(index));
            } else if (index < limit) {
                drawEmptySlot(inventory, offset);
            } else {
                drawLockedSlot(inventory, offset, tiers, index);
            }
        }
    }

    private void drawHome(Inventory inventory, int slot, Player target, String home) {
        String reference = context.icons().icon(targetId, home);
        if (reference == null) {
            reference = context.config().defaultIcon();
        }
        Location location = target == null ? null : context.essentials().home(target, home);
        // The status word is server text and may carry its own colour tags, so it is substituted
        // as-is. A world name is not: it is escaped, because only the untrusted side needs it and
        // escaping the status word would print its tags as literal text.
        String world = location == null || location.getWorld() == null
                ? context.messages().raw("status.none", "<#315a7a>None")
                : MessageService.escape(location.getWorld().getName());

        ItemStack item = context.items().button(
                reference,
                "menu.homes.home.name",
                "menu.homes.home.lore",
                Material.LIGHT_BLUE_BED,
                "%home%", MessageService.escape(home),
                "%world%", world,
                "%x%", location == null ? "?" : String.valueOf(location.getBlockX()),
                "%y%", location == null ? "?" : String.valueOf(location.getBlockY()),
                "%z%", location == null ? "?" : String.valueOf(location.getBlockZ()));
        inventory.setItem(slot, item);

        handlers().put(slot, (player, click) -> {
            context.playClick(player);
            if (click.isShiftClick()) {
                if (!player.hasPermission("polaroidhomes.icon")) {
                    context.messages().send(player, "general.no_permission");
                    return;
                }
                context.registry().open(player, new IconMenu(context, player, targetId, home, this));
                return;
            }
            if (!ownWindow) {
                // An admin looking at somebody else's homes is inspecting, not travelling. Moving
                // them to another player's bedroom on a stray click helps nobody.
                return;
            }
            player.closeInventory();
            onTeleport.run(player, home);
        });
    }

    private void drawEmptySlot(Inventory inventory, int slot) {
        inventory.setItem(slot, context.items().button(
                context.config().emptySlotIcon(),
                "menu.homes.empty.name",
                "menu.homes.empty.lore",
                Material.LIME_STAINED_GLASS_PANE));

        handlers().put(slot, (player, click) -> {
            if (!ownWindow) {
                return;
            }
            context.playClick(player);
            player.closeInventory();
            // The message carries the click action that writes the command, so the player never has
            // to type a command they have just been told about.
            context.messages().send(player, "homes.slot_empty");
        });
    }

    private void drawLockedSlot(Inventory inventory, int slot, List<HomeTier> tiers, int index) {
        HomeTier unlocking = HomeTier.unlockingSlot(tiers, index);
        // A group name comes from EssentialsX's config, not from a player, but it is escaped
        // anyway: an operator who names a group after a MiniMessage tag should get a strange label,
        // not a lore line that recolours the rest of the menu.
        String group = unlocking == null
                ? context.messages().raw("status.none", "<#315a7a>None")
                : MessageService.escape(unlocking.group());

        inventory.setItem(slot, context.items().button(
                context.config().lockedSlotIcon(),
                "menu.homes.locked.name",
                "menu.homes.locked.lore",
                Material.IRON_BARS,
                "%group%", group));

        // No handler: a locked slot does nothing, so it also stays silent. A click sound on a
        // button that refuses the click reads as a malfunction.
    }

    private void drawNavigation(Player viewer, Inventory inventory) {
        int pages = layout.pageCount();

        if (page > 0) {
            inventory.setItem(layout.previousPageSlot(), context.items().button(
                    "ARROW", "menu.homes.previous_page.name", "menu.homes.previous_page.lore",
                    Material.ARROW));
            handlers().put(layout.previousPageSlot(), (player, click) -> {
                context.playClick(player);
                page--;
                render(player);
            });
        }

        if (page < pages - 1) {
            inventory.setItem(layout.nextPageSlot(), context.items().button(
                    "ARROW", "menu.homes.next_page.name", "menu.homes.next_page.lore",
                    Material.ARROW));
            handlers().put(layout.nextPageSlot(), (player, click) -> {
                context.playClick(player);
                page++;
                render(player);
            });
        }

        Player target = ownWindow ? viewer : Bukkit.getPlayer(targetId);
        int used = target == null ? 0 : context.essentials().homes(target).size();
        int limit = target == null ? 0 : context.essentials().homeLimit(target);
        inventory.setItem(layout.infoSlot(), context.items().button(
                "PAPER", "menu.homes.info.name", "menu.homes.info.lore", Material.PAPER,
                "%used%", String.valueOf(used),
                "%limit%", String.valueOf(limit),
                "%page%", String.valueOf(page + 1),
                "%pages%", String.valueOf(pages)));

        inventory.setItem(layout.closeSlot(), context.items().button(
                "BARRIER", "menu.homes.close.name", "menu.homes.close.lore", Material.BARRIER));
        handlers().put(layout.closeSlot(), (player, click) -> {
            context.playClick(player);
            player.closeInventory();
        });
    }

    public GridLayout layout() {
        return layout;
    }

    /** Lets a child window build a replacement grid that teleports the same way this one does. */
    public HomeAction teleportAction() {
        return onTeleport;
    }
}
