package dev.zerep.zeah.gui;

import dev.zerep.zeah.ZeAuctionHouse;
import dev.zerep.zeah.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sell GUI — no item in hand required, no chat/Anvil input.
 * Fully Geyser / Bedrock compatible.
 *
 * Layout (54 slots / 6 rows):
 *   Rows 0-2 (0-26): player main inventory   (Bukkit slots 9-35)
 *   Row  3   (27-35): player hotbar           (Bukkit slots 0-8)
 *   Row  4   (36-44): [-100][-10][-1][SELECTED][+1][+10][+100][■][■]
 *   Row  5   (45-53): [BACK][■][■][■][PRICE][■][■][■][CONFIRM]
 */
public class SellGUI extends AuctionGUI {

    // Mapping: guiSlot → Bukkit inventory slot
    // Slots 0-26  → Bukkit 9-35  (main inventory rows 1-3)
    // Slots 27-35 → Bukkit 0-8   (hotbar)
    private static final int INV_DISPLAY_SIZE = 36; // 27 main + 9 hotbar
    private static final int SLOT_SEL = 40;   // selected item preview
    private static final int SLOT_PRICE = 49; // price display
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CONFIRM = 53;

    private ItemStack selectedItem;
    private int selectedInvSlot = -1; // Bukkit inv slot of the selected item
    private int price;
    private final int minPrice;
    private final int maxPrice;

    public SellGUI(ZeAuctionHouse plugin, Player player) {
        super(plugin, player);
        this.minPrice = plugin.getConfig().getInt("limits.min-price", 1);
        this.maxPrice = plugin.getConfig().getInt("limits.max-price", 9999);
        this.price = minPrice;
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(new ZeAHHolder(), 54,
            ColorUtil.color(plugin.getLang().getNoPrefix("gui.sell.title")));
        render();
        player.openInventory(inventory);
        register();
    }

    private void render() {
        // ── Rows 0-3: player inventory display ────────────────────────────
        for (int guiSlot = 0; guiSlot < INV_DISPLAY_SIZE; guiSlot++) {
            int bukkit = guiToBukkitSlot(guiSlot);
            ItemStack real = player.getInventory().getItem(bukkit);
            if (real == null || real.getType().isAir()) {
                inventory.setItem(guiSlot, filler());
                continue;
            }

            boolean sellable = isSellable(real);
            boolean selected = guiSlot == guiSlotOf(selectedInvSlot);

            ItemStack display = real.clone();
            ItemMeta meta = display.getItemMeta();
            List<net.kyori.adventure.text.Component> lore =
                meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.empty());
            if (selected) {
                lore.add(ColorUtil.color("&a✔ Selected"));
            } else if (sellable) {
                lore.add(ColorUtil.color("&eClick &7to select for sale"));
            } else {
                lore.add(ColorUtil.color("&c✗ Cannot sell this item"));
            }
            meta.lore(lore);
            if (selected) meta.setEnchantmentGlintOverride(true);
            display.setItemMeta(meta);
            inventory.setItem(guiSlot, display);
        }

        // ── Row 4: price controls ──────────────────────────────────────────
        inventory.setItem(36, buildItem(Material.RED_CONCRETE,    "&c-100"));
        inventory.setItem(37, buildItem(Material.ORANGE_CONCRETE, "&c-10"));
        inventory.setItem(38, buildItem(Material.YELLOW_CONCRETE, "&c-1"));

        // Selected item preview (center of control row)
        if (selectedItem != null) {
            ItemStack preview = selectedItem.clone();
            ItemMeta m = preview.getItemMeta();
            List<net.kyori.adventure.text.Component> l =
                m.hasLore() ? new ArrayList<>(m.lore()) : new ArrayList<>();
            l.add(net.kyori.adventure.text.Component.empty());
            l.add(ColorUtil.color("&7Selected item"));
            m.lore(l);
            preview.setItemMeta(m);
            inventory.setItem(SLOT_SEL, preview);
        } else {
            inventory.setItem(SLOT_SEL, buildItem(Material.BARRIER,
                "&c&lNo item selected",
                List.of("&7Click an item above to select it")));
        }

        inventory.setItem(41, buildItem(Material.LIME_CONCRETE,   "&a+1"));
        inventory.setItem(42, buildItem(Material.CYAN_CONCRETE,   "&a+10"));
        inventory.setItem(43, buildItem(Material.BLUE_CONCRETE,   "&a+100"));
        inventory.setItem(44, filler());

        // ── Row 5: action bar ─────────────────────────────────────────────
        inventory.setItem(SLOT_BACK, buildItem(Material.ARROW, "&7« Back", null));

        for (int i = 46; i <= 48; i++) inventory.setItem(i, filler());

        inventory.setItem(SLOT_PRICE, buildItem(Material.GOLD_NUGGET,
            "&e&lPrice: &6" + plugin.getEconomy().format(price),
            List.of(
                "&7Min: " + plugin.getEconomy().format(minPrice),
                "&7Max: " + plugin.getEconomy().format(maxPrice)
            )));

        for (int i = 50; i <= 52; i++) inventory.setItem(i, filler());

        boolean canConfirm = selectedItem != null;
        inventory.setItem(SLOT_CONFIRM, buildItem(
            canConfirm ? Material.EMERALD : Material.GRAY_STAINED_GLASS_PANE,
            canConfirm ? "&a&lList Item" : "&7Select an item first",
            canConfirm ? List.of(
                "&7Item: &f" + ColorUtil.formatMaterial(selectedItem.getType().name()),
                "&7Price: &6" + plugin.getEconomy().format(price),
                "",
                "&eClick to continue"
            ) : null));
    }

    @Override
    public void handleClick(int slot, ClickType click, InventoryClickEvent event) {
        event.setCancelled(true);

        // ── Item selection area (rows 0-3) ────────────────────────────────
        if (slot < INV_DISPLAY_SIZE) {
            int bukkit = guiToBukkitSlot(slot);
            ItemStack item = player.getInventory().getItem(bukkit);
            if (item == null || item.getType().isAir()) return;
            if (!isSellable(item) && !player.hasPermission("zeah.bypass.blacklist")) return;
            selectedItem = item.clone();
            selectedInvSlot = bukkit;
            render();
            return;
        }

        // ── Price controls ────────────────────────────────────────────────
        switch (slot) {
            case 36 -> { price = Math.max(minPrice, price - 100); render(); }
            case 37 -> { price = Math.max(minPrice, price - 10);  render(); }
            case 38 -> { price = Math.max(minPrice, price - 1);   render(); }
            case 41 -> { price = Math.min(maxPrice, price + 1);   render(); }
            case 42 -> { price = Math.min(maxPrice, price + 10);  render(); }
            case 43 -> { price = Math.min(maxPrice, price + 100); render(); }
            case SLOT_BACK    -> { player.closeInventory(); new MainMenuGUI(plugin, player).open(); }
            case SLOT_CONFIRM -> attemptConfirm();
        }
    }

    private void attemptConfirm() {
        if (selectedItem == null) {
            player.sendMessage(plugin.getLang().format("auction.hold-item")); return;
        }
        if (plugin.getSessionManager().hasSession(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().format("auction.session-active")); return;
        }
        // Verify item still in inventory
        ItemStack real = player.getInventory().getItem(selectedInvSlot);
        if (real == null || real.getType() != selectedItem.getType()) {
            player.sendMessage(plugin.getLang().format("auction.hold-item"));
            selectedItem = null; selectedInvSlot = -1;
            render(); return;
        }
        if (!plugin.getSessionManager().createSession(player, selectedItem)) {
            player.sendMessage(plugin.getLang().format("auction.session-active")); return;
        }
        plugin.getSessionManager().getSession(player.getUniqueId()).ifPresent(session -> {
            session.setPrice(price);
            // Remove one stack of the item from player inventory
            player.getInventory().setItem(selectedInvSlot, null);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                new ConfirmSellGUI(plugin, player, session).open());
        });
    }

    /** guiSlot (0-35) → Bukkit inventory slot */
    private static int guiToBukkitSlot(int guiSlot) {
        return guiSlot < 27 ? guiSlot + 9 : guiSlot - 27;
    }

    /** Bukkit inv slot → gui slot (inverse of above) */
    private static int guiSlotOf(int bukkit) {
        if (bukkit < 0) return -1;
        return bukkit < 9 ? bukkit + 27 : bukkit - 9;
    }

    private boolean isSellable(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (item.getType() == plugin.getEconomy().getCurrencyMaterial()) return false;
        Set<String> blacklist = plugin.getConfig().getStringList("blacklist")
            .stream().map(String::toUpperCase).collect(Collectors.toSet());
        return !blacklist.contains(item.getType().name());
    }
}
