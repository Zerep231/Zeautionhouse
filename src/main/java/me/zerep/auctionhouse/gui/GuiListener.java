package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.delivery.Delivery;
import me.zerep.auctionhouse.listing.ListingService;
import me.zerep.auctionhouse.session.CreateSession;
import me.zerep.auctionhouse.shop.ShopService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all inventory events for AH GUIs.
 *
 * P0.5 Fix – Every dupe vector is blocked:
 *   NUMBER_KEY, SWAP_OFFHAND, DOUBLE_CLICK, COLLECT_TO_CURSOR, CREATIVE mode drops.
 *   InventoryDragEvent cancels all drags except the CREATE_1 input slot.
 *
 * P0.3 Fix – SessionManager replaces per-player metadata keys.
 * P0.4 Fix – Real item is moved into session on Next click; GUI holds only clone.
 * P1.1 Fix – Click handlers use AhHolder.getListing/getDelivery() slot maps.
 */
public class GuiListener implements Listener {

    private final AuctionHousePlugin plugin;
    private final GuiManager gui;

    public GuiListener(AuctionHousePlugin plugin, GuiManager gui) {
        this.plugin = plugin;
        this.gui    = gui;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main click dispatcher
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof AhHolder holder)) return;

        int raw      = e.getRawSlot();
        boolean topClick = raw >= 0 && raw < top.getSize();

        switch (holder.tag()) {
            case CREATE_1  -> handleCreate1(p, e, raw, topClick, top, holder);
            case CREATE_2  -> handleCreate2(p, e, raw, topClick, holder);
            case DELIVERY  -> handleDelivery(p, e, raw, topClick, holder);
            case BROWSE    -> handleBrowse(p, e, raw, topClick, holder);
            case MINE      -> handleMine(p, e, raw, topClick, holder);
            case SHOP_HOME -> handleShopHome(p, e, raw, topClick, holder);
            case SHOP_CAT  -> handleShopCat(p, e, raw, topClick, holder);
            case SHOP_CONFIRM -> handleShopConfirm(p, e, raw, topClick, holder);
            default -> e.setCancelled(true);
        }
    }

    // ─── CREATE STEP 1 ───────────────────────────────────────────────────────

    private void handleCreate1(Player p, InventoryClickEvent e,
                                int raw, boolean topClick,
                                Inventory top, AhHolder holder) {
        // P0.5 – Block all dupe vectors
        if (isDupeAction(e)) { e.setCancelled(true); return; }

        if (!topClick) {
            // Bottom inventory: block shift-click in
            if (isShiftClick(e) || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
            }
            return;
        }

        if (raw == CreateListingGui.INPUT_SLOT) {
            // Allow normal place/pickup only; block everything else
            e.setCancelled(false);
            return;
        }

        e.setCancelled(true); // cancel all other top slots by default

        if (raw == CreateListingGui.CANCEL_SLOT) {
            // Return any item still in the slot, clear session
            ItemStack inSlot = top.getItem(CreateListingGui.INPUT_SLOT);
            if (inSlot != null && !inSlot.getType().isAir()) {
                top.setItem(CreateListingGui.INPUT_SLOT, null);
                giveOrDrop(p, inSlot);
            }
            plugin.getSessionManager().returnItem(p);
            p.closeInventory();
            return;
        }

        if (raw == CreateListingGui.NEXT_SLOT) {
            ItemStack inSlot = top.getItem(CreateListingGui.INPUT_SLOT);
            if (inSlot == null || inSlot.getType().isAir()) {
                p.sendMessage(plugin.msg("sell-no-item"));
                return;
            }
            // P0.4 – Take real item NOW; GUI gets a clean slot
            ItemStack realItem = inSlot.clone();
            top.setItem(CreateListingGui.INPUT_SLOT, null);

            String defaultCurrency = plugin.getCurrencyRegistry().defaultKey();
            int defaultPrice = plugin.getConfig().getInt("default-price", 1);
            var session = plugin.getSessionManager().start(p.getUniqueId(), realItem, defaultCurrency, defaultPrice);

            // P0-1 Fix: mark transitioning BEFORE scheduling step 2 so that the
            // InventoryCloseEvent fired when CREATE_1 closes does NOT call returnItem()
            session.setTransitioning(true);

            // Delay one tick so inventory close fires cleanly before opening step 2
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openCreateStep2(p));
        }
    }

    // ─── CREATE STEP 2 ───────────────────────────────────────────────────────

    private void handleCreate2(Player p, InventoryClickEvent e,
                                int raw, boolean topClick, AhHolder holder) {
        e.setCancelled(true);
        if (!topClick) return;

        CreateSession session = plugin.getSessionManager().get(p.getUniqueId());
        if (session == null) { p.closeInventory(); return; }

        // Back
        if (raw == CreateListingGui.BACK_SLOT) {
            plugin.getSessionManager().returnItem(p);
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openCreate(p));
            return;
        }

        // Confirm
        if (raw == CreateListingGui.CONFIRM_SLOT) {
            int max = plugin.getConfig().getInt("max-per-player", 10);
            if (!p.hasPermission("ah.bypass-limit")
                    && plugin.getListingService().countActive(p) >= max) {
                p.sendMessage(plugin.msg("sell-limit"));
                plugin.getSessionManager().returnItem(p);
                p.closeInventory();
                return;
            }
            // P2-1 Fix: enforce max-price in GUI confirm (only quick-sell validated it before)
            int maxPrice = plugin.getConfig().getInt("max-price", 999999);
            if (session.getPrice() > maxPrice) {
                p.sendMessage(plugin.msg("sell-price-too-high"));
                return;
            }
            session.markConfirmed();
            int id = plugin.getListingService().createListing(
                    p, session.getItem(), session.getPrice(), session.getCurrency());
            plugin.getSessionManager().remove(p.getUniqueId());
            if (id > 0) p.sendMessage(plugin.msg("sell-success"));
            else        p.sendMessage(plugin.msg("sell-error"));
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openBrowse(p, 0));
            return;
        }

        // Price adjustments
        if (raw == CreateListingGui.PRICE_MINUS10) { session.setPrice(session.getPrice() - 10); gui.openCreateStep2(p); return; }
        if (raw == CreateListingGui.PRICE_MINUS1)  { session.setPrice(session.getPrice() - 1);  gui.openCreateStep2(p); return; }
        if (raw == CreateListingGui.PRICE_PLUS1)   { session.setPrice(session.getPrice() + 1);  gui.openCreateStep2(p); return; }
        if (raw == CreateListingGui.PRICE_PLUS10)  { session.setPrice(session.getPrice() + 10); gui.openCreateStep2(p); return; }

        // Currency selector row (slots 19-25)
        if (raw >= CreateListingGui.CURRENCY_START && raw <= 25) {
            int idx = raw - CreateListingGui.CURRENCY_START;
            var currencies = plugin.getCurrencyRegistry().getAll();
            if (idx < currencies.size()) {
                session.setCurrency(currencies.get(idx).key());
                gui.openCreateStep2(p);
            }
        }
    }

    // ─── DELIVERY ────────────────────────────────────────────────────────────

    private void handleDelivery(Player p, InventoryClickEvent e,
                                 int raw, boolean topClick, AhHolder holder) {
        e.setCancelled(true);
        if (!topClick) return;

        if (raw == DeliveryGui.SLOT_BACK) {
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openBrowse(p, 0));
            return;
        }

        // P1-5: delivery pagination
        if (raw == DeliveryGui.SLOT_PREV) {
            int pg = Math.max(0, holder.page() - 1);
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openDelivery(p, pg));
            return;
        }
        if (raw == DeliveryGui.SLOT_NEXT) {
            int pg = holder.page() + 1;
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openDelivery(p, pg));
            return;
        }

        if (raw == DeliveryGui.SLOT_CLAIM_ALL) {
            int count = plugin.getDeliveryService().claimAll(p);
            p.sendMessage(count > 0
                    ? plugin.msg("claim-success").replace("{desc}", "x" + count + " items")
                    : plugin.msg("claim-none"));
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openDelivery(p, 0));
            return;
        }

        // Individual claim via slot map (P1.1 fix)
        Integer deliveryId = holder.getDelivery(raw);
        if (deliveryId == null) return;
        String desc = plugin.getDeliveryService().claim(p, deliveryId);
        if (desc != null) p.sendMessage(plugin.msg("claim-success").replace("{desc}", desc));
        else              p.sendMessage(plugin.msg("claim-none"));
        plugin.getServer().getScheduler().runTask(plugin, () -> gui.openDelivery(p, holder.page()));
    }

    // ─── BROWSE ──────────────────────────────────────────────────────────────

    private void handleBrowse(Player p, InventoryClickEvent e,
                               int raw, boolean topClick, AhHolder holder) {
        e.setCancelled(true);
        if (!topClick) return;

        if (raw == BrowseGui.SLOT_DELIVERIES) { plugin.getServer().getScheduler().runTask(plugin, () -> gui.openDelivery(p, 0));  return; }
        if (raw == BrowseGui.SLOT_MINE)       { plugin.getServer().getScheduler().runTask(plugin, () -> gui.openMine(p, 0));      return; }
        if (raw == BrowseGui.SLOT_REFRESH)    { plugin.getServer().getScheduler().runTask(plugin, () -> gui.openBrowse(p, 0));   return; }
        if (raw == BrowseGui.SLOT_SHOP)       { plugin.getServer().getScheduler().runTask(plugin, () -> gui.openShopHome(p));    return; }

        // Pagination
        if (raw == BrowseGui.SLOT_PREV) { parsePage(holder, -1, p); return; }
        if (raw == BrowseGui.SLOT_NEXT) { parsePage(holder, +1, p); return; }

        // Buy via slot map (P1.1 fix)
        Integer listingId = holder.getListing(raw);
        if (listingId == null) return;

        ListingService.BuyResult result = plugin.getListingService().purchase(p, listingId);
        switch (result) {
            case SUCCESS           -> p.sendMessage(plugin.msg("buy-success"));
            case LISTING_GONE      -> p.sendMessage(plugin.msg("listing-gone"));
            case OWN_LISTING       -> p.sendMessage(plugin.msg("buy-own-item"));
            case NOT_ENOUGH_CURRENCY -> {
                var l = plugin.getListingRepository().getById(listingId);
                String cur = l != null ? plugin.getCurrencyRegistry().displayName(l.currency()) : "?";
                String amt = l != null ? String.valueOf(l.price()) : "?";
                p.sendMessage(plugin.msg("buy-not-enough")
                        .replace("{amount}", amt).replace("{currency}", cur));
            }
            default -> {}
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> gui.openBrowse(p, 0));
    }

    private void parsePage(AhHolder holder, int delta, Player p) {
        // P0-5 Fix: read page directly from holder instead of fragile string parsing
        int newPage = Math.max(0, holder.page() + delta);
        plugin.getServer().getScheduler().runTask(plugin, () -> gui.openBrowse(p, newPage));
    }

    // ─── MINE ────────────────────────────────────────────────────────────────

    private void handleMine(Player p, InventoryClickEvent e,
                             int raw, boolean topClick, AhHolder holder) {
        e.setCancelled(true);
        if (!topClick) return;

        if (raw == MineGui.SLOT_BACK) { plugin.getServer().getScheduler().runTask(plugin, () -> gui.openBrowse(p, 0)); return; }
        if (raw == MineGui.SLOT_PREV) { parseMine(holder, -1, p); return; }
        if (raw == MineGui.SLOT_NEXT) { parseMine(holder, +1, p); return; }

        Integer listingId = holder.getListing(raw);
        if (listingId == null) return;
        if (plugin.getListingService().cancelListing(p, listingId))
            p.sendMessage(plugin.msg("sell-cancelled"));
        plugin.getServer().getScheduler().runTask(plugin, () -> gui.openMine(p, 0));
    }

    private void parseMine(AhHolder holder, int delta, Player p) {
        // P0-5 Fix: read page directly from holder
        int np = Math.max(0, holder.page() + delta);
        plugin.getServer().getScheduler().runTask(plugin, () -> gui.openMine(p, np));
    }

    // ─── SHOP HOME ───────────────────────────────────────────────────────────

    private void handleShopHome(Player p, InventoryClickEvent e,
                                 int raw, boolean topClick, AhHolder holder) {
        e.setCancelled(true);
        if (!topClick) return;
        if (raw == 22) { plugin.getServer().getScheduler().runTask(plugin, () -> gui.openBrowse(p, 0)); return; }
        String catId = holder.getCategory(raw);
        if (catId == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> gui.openShopCategory(p, catId));
    }

    // ─── SHOP CATEGORY ───────────────────────────────────────────────────────

    private void handleShopCat(Player p, InventoryClickEvent e,
                                int raw, boolean topClick, AhHolder holder) {
        e.setCancelled(true);
        if (!topClick) return;
        if (raw == 49) { plugin.getServer().getScheduler().runTask(plugin, () -> gui.openShopHome(p)); return; }
        String compositeId = holder.getItemId(raw); // "catId:itemId"
        if (compositeId == null) return;
        String[] parts = compositeId.split(":", 2);
        if (parts.length < 2) return;
        ShopService.ShopEntry entry = plugin.getShopService().getEntry(parts[0], parts[1]);
        if (entry == null) return;
        Material defaultCur = entry.currencies().isEmpty() ? Material.DIAMOND : entry.currencies().get(0);
        plugin.getServer().getScheduler().runTask(plugin,
                () -> gui.openShopConfirm(p, parts[0], parts[1], defaultCur));
    }

    // ─── SHOP CONFIRM ────────────────────────────────────────────────────────

    private void handleShopConfirm(Player p, InventoryClickEvent e,
                                    int raw, boolean topClick, AhHolder holder) {
        e.setCancelled(true);
        if (!topClick) return;
        // key format: "shop-confirm:catId:itemId"
        String key = holder.key();
        String[] parts = key.split(":", 3);
        if (parts.length < 3) { p.closeInventory(); return; }
        String catId  = parts[1];
        String itemId = parts[2];
        ShopService.ShopEntry entry = plugin.getShopService().getEntry(catId, itemId);
        if (entry == null) { p.closeInventory(); return; }

        if (raw == 15) { // Cancel
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openShopCategory(p, catId));
            return;
        }
        if (raw == 11 && entry != null) { // Buy x1
            Material currency = entry.currencies().isEmpty() ? Material.DIAMOND : entry.currencies().get(0);
            boolean ok = plugin.getShopService().buy(p, entry, currency, 1, plugin.getDeliveryRepository());
            p.sendMessage(ok ? plugin.msg("shop-buy-success") : plugin.msg("buy-not-enough")
                    .replace("{amount}", String.valueOf(entry.price()))
                    .replace("{currency}", currency.name().replace('_', ' ')));
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openShopCategory(p, catId));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drag event – P0.5
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof AhHolder holder)) return;

        if (holder.tag() == GuiTag.CREATE_1) {
            // Allow drag only into the input slot; cancel if any affected slot is NOT the input slot
            for (int slot : e.getRawSlots()) {
                if (slot < top.getSize() && slot != CreateListingGui.INPUT_SLOT) {
                    e.setCancelled(true);
                    return;
                }
            }
            // Also cancel if dragging from bottom inventory into top
            if (e.getRawSlots().stream().anyMatch(s -> s >= top.getSize())) {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true); // cancel all drags in every other AH GUI
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Close event – P0.4 item safety
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof AhHolder holder)) return;

        if (holder.tag() == GuiTag.CREATE_1) {
            // If player closes without clicking Next, return any item still in the input slot
            ItemStack item = top.getItem(CreateListingGui.INPUT_SLOT);
            if (item != null && !item.getType().isAir()) {
                top.setItem(CreateListingGui.INPUT_SLOT, null);
                giveOrDrop(p, item);
            }
            // Also return any session item that may exist (e.g. force-closed)
            plugin.getSessionManager().returnItem(p);
        }

        if (holder.tag() == GuiTag.CREATE_2) {
            // Session item is returned only if the listing was NOT confirmed
            plugin.getSessionManager().returnItem(p);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** P0.5 – Returns true for all known dupe-vector click types. */
    private boolean isDupeAction(InventoryClickEvent e) {
        return switch (e.getClick()) {
            case NUMBER_KEY, SWAP_OFFHAND, DOUBLE_CLICK, CREATIVE -> true;
            default -> e.getAction() == InventoryAction.COLLECT_TO_CURSOR;
        };
    }

    private boolean isShiftClick(InventoryClickEvent e) {
        return e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT;
    }

    private void giveOrDrop(Player p, ItemStack item) {
        var leftovers = p.getInventory().addItem(item);
        leftovers.values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
        p.updateInventory();
    }
}
