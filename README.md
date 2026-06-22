<div align="center">

# 🏷️ ZeAuctionHouse

**A vanilla-currency player marketplace for Paper 1.21+ with Geyser/Bedrock support.**

[![Version](https://img.shields.io/badge/version-3.2.2-gold?style=flat-square)](https://github.com/Zerep231/Zeautionhouse/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21.1+-f96854?style=flat-square&logo=data:image/png;base64,)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square&logo=openjdk)](https://adoptium.net)
[![Geyser](https://img.shields.io/badge/Geyser-supported-00b4d8?style=flat-square)](https://geysermc.org)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](LICENSE)

</div>

---

## ✨ Features

| Feature | Description |
|---|---|
| 🛒 **Player Marketplace** | Buy and sell any item using in-game materials as currency |
| 📦 **Delivery Box** | Safe async delivery — purchased items are held until the player claims them |
| 🏪 **Build Shop** | Server-managed category shop for blocks and decoration |
| 🔒 **Anti-Dupe Protection** | All known GUI exploit vectors are blocked (hotbar swap, double-click, drag, off-hand) |
| ⚛️ **Atomic Transactions** | SQLite `BEGIN IMMEDIATE` ensures no double-sells or race conditions |
| 📋 **Audit Log** | Every purchase, cancellation, and expiry is recorded in the database |
| 📱 **Bedrock / Geyser** | Full GUI support + `/ah sell <price>` command shortcut for mobile players |
| ♻️ **Auto-Expiry** | Listings expire after a configurable time; item is returned to seller automatically |

---

## 📋 Requirements

| Dependency | Version | Required |
|---|---|---|
| [Paper](https://papermc.io/downloads) | 1.21.1 or newer | ✅ Required |
| Java | 21 or newer | ✅ Required |
| [Geyser](https://geysermc.org) + [Floodgate](https://github.com/GeyserMC/Floodgate) | Any | ⬜ Optional (Bedrock support) |

> SQLite is bundled — no external database setup needed.

---

## 🚀 Installation

1. Download `ZeAuctionHouse.jar` from [Releases](https://github.com/Zerep231/Zeautionhouse/releases)
2. Drop it into your server's `plugins/` folder
3. Start (or restart) the server — config files generate automatically
4. Edit `plugins/AuctionHouse/config.yml` to your liking
5. Use `/ah reload` to apply config changes without restarting

---

## 🎮 Commands & Permissions

### Commands

| Command | Aliases | Description |
|---|---|---|
| `/ah` | `/auction`, `/auctionhouse` | Open the Browse marketplace |
| `/ah sell` | | Open the sell GUI (drag item → set price → confirm) |
| `/ah sell <price> [currency]` | | Quick-sell item in hand without GUI *(great for Bedrock)* |
| `/ah mine` | | View and manage your active listings |
| `/ah deliveries` | `/ah claim`, `/ah box` | Open your Delivery Box to claim items and currency |
| `/ah shop` | | Open the Build Shop |
| `/ah reload` | | Reload config *(admin only)* |

### Permissions

| Permission | Default | Description |
|---|---|---|
| `ah.use` | Everyone | Access all player-facing commands |
| `ah.admin` | OP | Use `/ah reload` |
| `ah.bypass-limit` | OP | Post more listings than `max-per-player` allows |

---

## 🖼️ GUI Flow

### Selling an Item (Java)
```
/ah sell
  └─ Step 1: Place item in the center slot → click Next →
       └─ Step 2: Adjust price (±1 / ±10) → select currency → click ✔ Confirm
```

### Selling an Item (Bedrock / Geyser)
```
/ah sell <price> [currency]    ← quick-sell item in hand, no GUI needed

or use the GUI the same as Java — Geyser renders it with left-click placement
```

### Buying an Item
```
/ah  →  Browse listings  →  Left-click any item  →  confirm payment
  └─ Item appears in /ah deliveries (safe delivery, never lost)
```

### Claiming Items
```
/ah deliveries
  ├─ Left-click individual items to claim one at a time
  └─ Click "Claim All" to grab everything at once
```

---

## ⚙️ Configuration

`plugins/AuctionHouse/config.yml`

```yaml
# Maximum active listings a player can have at once
max-per-player: 10

# How many hours before a listing expires (item returned to seller)
listing-expire-hours: 72

# How often the plugin checks for expired listings (minutes)
expire-check-interval-minutes: 10

# Broadcast a server-wide message when a sale completes
broadcast-sales: true

# Hard cap on listing price
max-price: 999999

# Currencies available for trading (must be valid Bukkit Material names)
currencies:
  - DIAMOND
  - EMERALD
  - NETHERITE_INGOT
  - GOLD_INGOT
  - IRON_INGOT

# Display names for each currency (supports & color codes)
currency-display:
  DIAMOND: "&bDiamond"
  EMERALD: "&2Emerald"
  NETHERITE_INGOT: "&8Netherite"
  GOLD_INGOT: "&6Gold"
  IRON_INGOT: "&7Iron"
```

### Build Shop

Add categories and items under `shop:` in `config.yml`:

```yaml
shop:
  categories:
    blocks:
      title: "&aBlocks"
      items:
        stone:
          material: STONE
          amount: 64
          price: 1
          currencies: [IRON_INGOT, GOLD_INGOT, EMERALD, DIAMOND]
```

---

## 🗄️ Data Storage

| Table | Description |
|---|---|
| `listings` | All player listings (ACTIVE / SOLD / CANCELLED / EXPIRED) |
| `deliveries` | Pending item & currency deliveries (claimed flag) |
| `audit_log` | Immutable record of every purchase, cancel, and expiry |
| `shop_categories` | Build shop category definitions |
| `shop_items` | Build shop item definitions |

Database file: `plugins/AuctionHouse/auctionhouse.db` (SQLite, WAL mode)

---

## 🛡️ Security & Anti-Exploit

- **Atomic purchases** — `BEGIN IMMEDIATE` transaction with `WHERE status='ACTIVE'` guard prevents double-buying the same listing
- **Full inventory currency scan** — checks main inventory **and** off-hand slot; currency cannot be hidden to bypass price checks
- **Session-based listing flow** — real item is captured into server memory the moment the player clicks *Next*, the GUI only ever shows a clone; closing the GUI safely returns the item
- **GUI event hardening** — `NUMBER_KEY`, `SWAP_OFFHAND`, `DOUBLE_CLICK`, `COLLECT_TO_CURSOR`, `CREATIVE` mode clicks, and all drag events are cancelled in every AH GUI
- **Audit log** — every economic action is written to `audit_log` for admin review

---

## 🏗️ Building from Source

**Requirements:** Maven 3.8+, Java 21+

```bash
git clone https://github.com/Zerep231/Zeautionhouse.git
cd Zeautionhouse
mvn clean package
# Output: target/ZeAuctionHouse.jar
```

Dependencies are fetched automatically via Maven (Paper API, Floodgate API).  
SQLite is shaded and relocated to `me.zerep.auctionhouse.libs.sqlite` to avoid conflicts.

---

## 📜 Changelog

### v3.2.2
- Fix: off-hand slot now included in currency count/removal (bypass exploit patched)

### v3.2.1
- Fix: quick-sell (`/ah sell <price>`) now safely returns item to player on any DB error
- Fix: Step-1 GUI hint text corrected ("click Next →")

### v3.2.0 — Major Rewrite
- **P0** `GuiManager.java` added (was missing, caused build failure)
- **P0** Atomic purchase via `TransactionManager` (`BEGIN IMMEDIATE / COMMIT / ROLLBACK`)
- **P0** `SessionManager` + `CreateSession` replace unsafe metadata keys
- **P0** Full anti-dupe: NUMBER_KEY, SWAP_OFFHAND, DOUBLE_CLICK, drag events blocked
- **P1** `AhHolder` slot maps fix broken click → listing mapping on every page
- **P1** `claimAll()` uses a single atomic `DELETE IN (...)` query
- **P1** `AuditLogRepository` — every action recorded in `audit_log` table
- **P1** `ListingCache` — 30-second in-memory cache reduces DB load on GUI open
- **P2** Builder Shop: full Home → Category → Item → Confirm flow
- **P2** Bedrock support: Floodgate softdepend + `/ah sell <price>` command shortcut
- **P2** Adventure API (`LegacyComponentSerializer`) replaces deprecated `ChatColor`
- **P2** `ItemSerializer` modernised to Paper `ItemStack#serializeAsBytes`

### v3.1.1 (original)
- Initial public release

---

## 👤 Author

**Zerep** — [GitHub](https://github.com/Zerep231)

---

<div align="center">
<sub>Built for Paper 1.21.1+ · Vanilla economy · No external plugin dependencies</sub>
</div>
