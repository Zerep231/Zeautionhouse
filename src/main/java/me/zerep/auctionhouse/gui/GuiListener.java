package me.zerep.auctionhouse.gui;

import me.zerep.auctionhouse.AuctionHousePlugin;
import me.zerep.auctionhouse.delivery.Delivery;
import me.zerep.auctionhouse.listing.Listing;
import me.zerep.auctionhouse.listing.ListingService;
import me.zerep.auctionhouse.shop.ShopService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GuiListener implements Listener {
    private final AuctionHousePlugin plugin;
    private final GuiManager gui;

    public GuiListener(AuctionHousePlugin plugin, GuiManager gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof AhHolder holder)) return;

        int raw = e.getRawSlot();
        boolean topClick = raw < top.getSize();

        if (holder.tag() == GuiTag.CREATE_1) {
            handleCreate1(p, e, raw, topClick);
            return;
        }
        if (holder.tag() == GuiTag.CREATE_2) {
            handleCreate2(p, e, raw, topClick);
            return;
        }
        if (holder.tag() == GuiTag.DELIVERY) {
            handleDelivery(p, e, raw, topClick);
            return;
        }
        if (holder.tag() == GuiTag.BROWSE) {
            handleBrowse(p, e, raw, topClick);
            return;
        }
        if (holder.tag() == GuiTag.MINE) {
            handleMine(p, e, raw, topClick);
            return;
        }
        if (holder.tag() == GuiTag.SHOP_HOME) {
            handleShopHome(p, e, raw, topClick);
        }
    }

    private void handleCreate1(Player p, InventoryClickEvent e, int raw, boolean topClick) {
        ItemStack current = e.getCurrentItem();
        if (topClick) {
            if (raw == CreateListingGui.INPUT_SLOT) {
                if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.SWAP_OFFHAND || e.getClick() == ClickType.DOUBLE_CLICK || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                    e.setCancelled(true);
                } else {
                    e.setCancelled(false);
                }
            } else {
                e.setCancelled(true);
                if (raw == CreateListingGui.NEXT_SLOT && current != null && current.getType() != Material.AIR) {
                    ItemStack item = p.getOpenInventory().getTopInventory().getItem(CreateListingGui.INPUT_SLOT);
                    if (item != null && item.getType() != Material.AIR) {
                        gui.openCreate(p); // fallback, actual step2 is handled from open
                        // step2 will be opened in close-safe flow by listener
                    }
                }
            }
        } else {
            if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT || e.getClick() == ClickType.NUMBER_KEY || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
        }
    }

    private void handleCreate2(Player p, InventoryClickEvent e, int raw, boolean topClick) {
        e.setCancelled(true);
        if (!topClick) return;
        if (raw == 20) {
            p.closeInventory();
            return;
        }
        if (raw == 24) {
            ItemStack item = p.getMetadata("ah_create_item").isEmpty() ? null : (ItemStack) p.getMetadata("ah_create_item").get(0).value();
            Integer price = p.getMetadata("ah_create_price").isEmpty() ? null : (Integer) p.getMetadata("ah_create_price").get(0).value();
            String currency = p.getMetadata("ah_create_currency").isEmpty() ? null : (String) p.getMetadata("ah_create_currency").get(0).value();
            if (item != null && price != null && currency != null) {
                int id = plugin.getListingService().createListing(p, item, price, currency);
                if (id > 0) p.sendMessage(plugin.msg("sell-success"));
            }
        }
    }

    private void handleDelivery(Player p, InventoryClickEvent e, int raw, boolean topClick) {
        e.setCancelled(true);
        if (!topClick) return;
        if (raw == 49) {
            int count = plugin.getDeliveryService().claimAll(p);
            p.sendMessage(count > 0 ? plugin.msg("claim-success").replace("{desc}", "Claim All x" + count) : plugin.msg("claim-none"));
            gui.openDelivery(p, 0);
            return;
        }
        Delivery d = plugin.getDeliveryService().getUnclaimed(p.getUniqueId()).stream().filter(x -> x != null).skip(raw).findFirst().orElse(null);
        if (d != null) {
            String desc = plugin.getDeliveryService().claim(p, d.id());
            if (desc != null) p.sendMessage(plugin.msg("claim-success").replace("{desc}", desc));
            else p.sendMessage(plugin.msg("claim-none"));
            gui.openDelivery(p, 0);
        }
    }

    private void handleBrowse(Player p, InventoryClickEvent e, int raw, boolean topClick) {
        e.setCancelled(true);
        if (!topClick) return;
        if (raw == 45) { gui.openDelivery(p, 0); return; }
        if (raw == 48) { gui.openMine(p, 0); return; }
        if (raw == 49) { gui.openBrowse(p, 0); return; }
        if (raw == 53) { new ShopGui(plugin).openHome(p); return; }

        Listing l = plugin.getListingRepository().getActive(0, 54).stream().skip(raw).findFirst().orElse(null);
        if (l == null) return;
        ListingService.BuyResult result = plugin.getListingService().purchase(p, l.id());
        switch (result) {
            case SUCCESS -> p.sendMessage(plugin.msg("buy-success"));
            case LISTING_GONE -> p.sendMessage(plugin.msg("listing-gone"));
            case NOT_ENOUGH_CURRENCY -> p.sendMessage(plugin.msg("buy-not-enough").replace("{amount}", String.valueOf(l.price())).replace("{currency}", plugin.getCurrencyRegistry().displayName(l.currency())));
            case OWN_LISTING -> p.sendMessage(plugin.msg("buy-own-item"));
            default -> {}
        }
        gui.openBrowse(p, 0);
    }

    private void handleMine(Player p, InventoryClickEvent e, int raw, boolean topClick) {
        e.setCancelled(true);
        if (!topClick) return;
        if (raw == 49) {
            gui.openBrowse(p, 0);
            return;
        }
        Listing l = plugin.getListingRepository().getByPlayer(p.getUniqueId(), 0, 54).stream().skip(raw).findFirst().orElse(null);
        if (l == null) return;
        if (plugin.getListingService().cancelListing(p, l.id())) {
            p.sendMessage(plugin.msg("sell-cancelled"));
        }
        gui.openMine(p, 0);
    }

    private void handleShopHome(Player p, InventoryClickEvent e, int raw, boolean topClick) {
        e.setCancelled(true);
        if (!topClick) return;
        if (raw < 10 || raw > 20) return;
        int index = raw - 10;
        var cats = plugin.getShopService().getCategories();
        if (index >= cats.size()) return;
        ShopService.ShopCategory cat = cats.get(index);
        p.closeInventory();
        p.sendMessage("§aSelected category: §f" + cat.title());
        // simple next step: first item of category
        if (!cat.items().isEmpty()) {
            ShopService.ShopEntry entry = cat.items().get(0);
            // direct open confirm-like flow by just stating item; can be expanded later
            p.sendMessage("§7Use /ah shop again to browse. (§eV3.1 skeleton§7)");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof AhHolder holder)) return;
        if (holder.tag() == GuiTag.CREATE_1) {
            for (int raw : e.getRawSlots()) {
                if (raw < top.getSize() && raw != CreateListingGui.INPUT_SLOT) {
                    e.setCancelled(true);
                    return;
                }
            }
            if (e.getRawSlots().stream().anyMatch(raw -> raw >= top.getSize())) {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof AhHolder holder)) return;

        if (holder.tag() == GuiTag.CREATE_1) {
            ItemStack item = top.getItem(CreateListingGui.INPUT_SLOT);
            if (item != null && item.getType() != Material.AIR) {
                top.setItem(CreateListingGui.INPUT_SLOT, null);
                var leftovers = p.getInventory().addItem(item);
                for (ItemStack left : leftovers.values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                p.updateInventory();
            }
        }
    }
}
