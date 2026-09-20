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
import java.util.Map;
import java.util.UUID;

/**
 * The homes grid.
 *
 * <p>Slot classification, which decides which rules apply: <b>every slot is chrome</b>. The window
 * holds no item the player owns, so the correct behaviour is to cancel every click and drag and
 * dispatch an action instead. There is no persistence path to protect, which is what separates this
 * from a backpack or a vault. Making the layout data-driven does not change that: an operator can
 * move a slot, never turn one into storage.
 *
 * <p>The grid is sized at the server's maximum rather than at the viewer's own limit, so a player
 * can see what the ranks above them unlock. {@link GridLayout} caps that at
 * {@code max-displayed-slots} in menu.yml, which is what keeps an "unlimited" EssentialsX tier from
 * asking for more slots than the layout can page through.
 */
public final class HomesMenu extends MenuHolder {

    private final MenuContext context;
    private final UUID targetId;
    private final String targetName;
    private final boolean ownWindow;
    private final HomeAction onTeleport;

    // Fixed for the lifetime of the window: the inventory is sized from the template at
    // construction, so a mid-life change would produce a slot map the open window cannot hold. A
    // configuration reload takes effect on the next open, which is when a correctly sized window
    // can be built, and reloadEverything() closes every open window so nothing outlives its layout.
    private final MenuTemplate template;
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
        this.template = context.menus().homes();
        this.layout = computeLayout(viewer);
        setInventory(createInventory(viewer));
    }

    private GridLayout computeLayout(Player viewer) {
        List<HomeTier> tiers = context.essentials().tiers();
        // The fallback matters: with no tiers configured at all, the viewer's own resolved limit is
        // still a real number and still deserves a grid.
        int highest = HomeTier.highestLimit(tiers, context.essentials().homeLimit(viewer));
        return GridLayout.of(template, highest, context.menus().maxDisplayedSlots());
    }

    private Inventory createInventory(Player viewer) {
        String titleKey = ownWindow ? "menu.homes.title" : "menu.homes.title_other";
        // Escaped: the target's name is player-controlled on servers that allow renames, and an
        // unescaped one could open a tag in the title.
        return Bukkit.createInventory(this, template.inventorySize(),
                context.messages().build(titleKey, "%player%", MessageService.escape(targetName)));
    }

    @Override
    public void render(Player viewer) {
        beginUpdate();
        try {
            Inventory inventory = getInventory();
            inventory.clear();
            clearHandlers();

            drawChrome(inventory);
            drawHomes(viewer, inventory);
        } finally {
            // In a finally block on purpose: a render that throws halfway would otherwise leave the
            // latch up forever and every later click would be silently dropped.
            endUpdate();
        }
    }

    /**
     * Draws everything the template placed that is not a home slot.
     *
     * <p>A slot the template left blank stays empty and unhandled: only a declared filler puts an
     * item there. An element that does nothing — filler, info — registers no handler at all, so a
     * click on it is silent rather than playing the panel's sound for no result.
     */
    private void drawChrome(Inventory inventory) {
        int pages = layout.pageCount();

        for (Map.Entry<Integer, MenuElement> entry : template.slots().entrySet()) {
            int slot = entry.getKey();
            MenuElement element = entry.getValue();
            MenuTemplate.ElementDefinition definition = template.definition(element);

            switch (element) {
                case FILLER -> inventory.setItem(slot, filler(definition));
                case INFO -> inventory.setItem(slot, info(definition, pages));
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
                        // There is nowhere to go back to, so the slot reads as background rather
                        // than as a button that refuses the click.
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
                case CLOSE -> {
                    inventory.setItem(slot, context.items().element(definition, "BARRIER",
                            "menu.homes.close.name", "menu.homes.close.lore", Material.BARRIER));
                    handlers().put(slot, (player, click) -> {
                        context.playClick(player);
                        player.closeInventory();
                    });
                }
                // Home slots are drawn by drawHomes, in reading order across pages. The icon
                // elements belong to the picker and were rejected by validation for this menu.
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

    private ItemStack info(MenuTemplate.ElementDefinition definition, int pages) {
        Player target = Bukkit.getPlayer(targetId);
        int used = target == null ? 0 : context.essentials().homes(target).size();
        int limit = target == null ? 0 : context.essentials().homeLimit(target);
        return context.items().element(definition, "PAPER",
                "menu.homes.info.name", "menu.homes.info.lore", Material.PAPER,
                "%used%", String.valueOf(used),
                "%limit%", String.valueOf(limit),
                "%page%", String.valueOf(page + 1),
                "%pages%", String.valueOf(pages));
        // No handler: the counter is a label. A click sound on a label reads as a malfunction.
    }

    private void drawHomes(Player viewer, Inventory inventory) {
        Player target = ownWindow ? viewer : Bukkit.getPlayer(targetId);
        List<String> homes = target == null ? List.of() : context.essentials().homes(target);
        int limit = target == null ? 0 : context.essentials().homeLimit(target);
        List<HomeTier> tiers = context.essentials().tiers();

        List<Integer> positions = template.homeSlots();
        int first = layout.firstIndexOfPage(page);
        int count = layout.slotsOnPage(page);

        for (int offset = 0; offset < count && offset < positions.size(); offset++) {
            int index = first + offset;
            int slot = positions.get(offset);
            if (index < homes.size()) {
                drawHome(inventory, slot, target, homes.get(index));
            } else if (index < limit) {
                drawEmptySlot(inventory, slot);
            } else {
                drawLockedSlot(inventory, slot, tiers, index);
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

        // The home's own icon comes from the icon store, never from menu.yml, so this one button
        // keeps its reference and its text from the existing path rather than from the template.
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

    public GridLayout layout() {
        return layout;
    }

    /** Lets a child window build a replacement grid that teleports the same way this one does. */
    public HomeAction teleportAction() {
        return onTeleport;
    }
}
