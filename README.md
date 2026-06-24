# ZeAuctionHouse

[![Release](https://img.shields.io/github/v/release/Zerep231/Zeautionhouse?label=release&color=brightgreen)](https://github.com/Zerep231/Zeautionhouse/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/Zerep231/Zeautionhouse/build.yml?branch=main&label=build)](https://github.com/Zerep231/Zeautionhouse/actions/workflows/build.yml)
[![Paper](https://img.shields.io/badge/Paper-1.21.1%2B-orange)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21%2B-blue)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

> Lightweight auction house plugin for Paper 1.21.1+ — **no Vault, no virtual currency.**
> Players trade with real in-game items (Diamonds, Emeralds, etc.).

---

## ⬇️ Download

| Version | Status | Download |
|---|---|---|
| **v3.3.2** | ✅ Stable | [ZeAuctionHouse-3.3.2.jar](https://github.com/Zerep231/Zeautionhouse/releases/tag/v3.3.2) |
| v3.3.1 | Superseded | [v3.3.1](https://github.com/Zerep231/Zeautionhouse/releases/tag/v3.3.1) |
| v3.3.0 | Superseded | [Releases](https://github.com/Zerep231/Zeautionhouse/releases) |

---

## ✨ Key Features

| Feature | Description |
|---|---|
| 💎 **No Vault** | Economy based on physical items — no dependency plugins needed |
| 🏪 **Marketplace** | List, browse, buy, and cancel item listings via GUI |
| 🪨 **Builder Shop** | Buy building materials per category (stone, wood, glass, plants, deco, nether, end) with quantity selector |
| 📦 **Delivery Box** | Items held safely in mailbox — no item loss if inventory full |
| 💾 **Session Persistence** | Sell sessions saved to DB — survive server crashes and restarts |
| 🗄️ **SQLite & MySQL** | Choose your database with HikariCP connection pooling |
| 🌐 **Multi-language** | English and Vietnamese built-in |
| 📱 **Bedrock-friendly** | GUI compatible with Geyser/Floodgate |
| 📋 **Audit Logging** | Every transaction logged to file and database |

---

## 🎮 Commands

| Command | Permission | Description |
|---|---|---|
| `/ah` | `zeah.use` | Browse all listings |
| `/ah sell` | `zeah.sell` | Open sell GUI (price via +/- buttons) |
| `/ah claim` | `zeah.claim` | Collect items from mailbox |
| `/ah shop` | `zeah.shop` | Open builder shop |
| `/ah mylistings` | `zeah.use` | View & cancel your listings |
| `/ah reload` | `zeah.admin` | Reload config & shop files |

**Aliases:** `/auctionhouse`, `/auction`

---

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `zeah.use` | `true` | Browse and use auction house |
| `zeah.sell` | `true` | List items for sale |
| `zeah.claim` | `true` | Claim delivered items |
| `zeah.shop` | `true` | Use builder shop |
| `zeah.admin` | `op` | Reload config |
| `zeah.bypass.limit` | `op` | Bypass max listing count |
| `zeah.bypass.blacklist` | `op` | Sell blacklisted items |

---

## ⚙️ Installation

**Requirements:** Paper 1.21.1+ · Java 21+

1. Download `ZeAuctionHouse-x.x.x.jar` from [Releases](https://github.com/Zerep231/Zeautionhouse/releases)
2. Drop the JAR into your server's `plugins/` folder
3. Restart the server
4. Configure `plugins/ZeAuctionHouse/config.yml`

### Quick Config

```yaml
economy:
  currency-item: DIAMOND   # Material name — item used as currency
  currency-name: Diamond   # Display name shown in GUI/messages

database:
  type: sqlite             # sqlite | mysql
  # MySQL options (only needed when type: mysql):
  host: localhost
  port: 3306
  database: zeauctionhouse
  user: root
  password: ''
  pool-size: 10

lang: en                   # en | vi

limits:
  max-listings-per-player: 10
  max-price: 9999
  min-price: 1

expire:
  default-days: 7
```

---

## 📦 Builder Shop

Each shop category is a separate YAML file in `plugins/ZeAuctionHouse/shop/`.  
Default categories: `stone`, `wood`, `glass`, `plants`, `decoration`, `nether`, `end`

```yaml
# plugins/ZeAuctionHouse/shop/stone.yml
name: "Stone"
icon: STONE
items:
  - { material: STONE,      price: 1 }
  - { material: COBBLESTONE, price: 1 }
  - { material: DEEPSLATE,  price: 2 }
```

---

## 📝 Update History


### v3.3.2 — GUI Improvements & JAR Optimization

**Sell GUI**
- `/ah sell` now opens a full GUI (no longer requires price argument)
- Price set with +/- buttons (±1, ±10, ±100) — 100% Geyser/Bedrock compatible
- No Anvil GUI used, no chat input required

**Main Menu**
- `/ah` (no args) opens a hub GUI with all features accessible
- Browse · Sell · My Listings · Claim · Builder Shop

**JAR Size Reduction (~5–6 MB)**
- Excluded SQLite native binaries for Mac, Windows, FreeBSD, 32-bit ARM
- Kept: Linux x86\_64 and Linux aarch64 (covers all common server platforms)
- MySQL: excluded bundled protobuf/Google libs
- Updated Java compiler target: 17 → 21

**Bug Fixes**
- `GUIListener` now recognizes SellGUI and MainMenuGUI titles for click protection

---

### v3.3.1 — Stabilization *(current)*

> Focus: bug fixes and GUI improvements only. No new features.

**Bug Fixes**
- `ShopCategoryGUI` now correctly opens `ShopQuantityGUI` (quantity selector) — previously bought directly without quantity selection
- `MyListingsGUI` now requires **right-click** to cancel a listing — prevents accidental cancellation from left-click
- `ShopConfirmGUI` checks if player can afford items before confirming; shows balance and affordability status
- `ShopQuantityGUI` back-navigation properly returns to the correct shop category

**Infrastructure**
- Added `getCategoryByItem()` to `ShopManager` for GUI back-navigation
- `SessionManager` now persists draft sell sessions to `draft_listings` DB table — sessions survive restarts
- Added `Delivery.Status.FAILED` to delivery state machine for crash-safe claiming
- Added `getConnection()` to `DatabaseExecutor` interface, implemented in both `SQLiteExecutor` and `MySQLExecutor`
- Removed 28 duplicate files from old `me.zerep.auctionhouse` package — significant JAR size reduction

---

### v3.3.0 — Initial Stable Release

- Marketplace: list, browse, buy, cancel listings via GUI
- Builder Shop with per-category YAML files
- Delivery Box (mailbox) for safe item delivery
- SQLite + MySQL support with HikariCP pooling
- Session management for sell flow
- Item blacklist, listing limits, expiry system
- Audit logging (file + database)
- English and Vietnamese language support
- `/ah reload` admin command
- Bedrock/Geyser-compatible GUI

---

## 🛣️ Roadmap

| Version | Feature |
|---|---|
| v4.0 | Buy Orders |
| v4.1 | Item Exchange (trade offers) |
| v4.2 | Discord integration |
| Future | Public REST API |

---

## 🏗️ Building from Source

```bash
git clone https://github.com/Zerep231/Zeautionhouse.git
cd Zeautionhouse/AuctionHouse
mvn clean package
# Output: target/ZeAuctionHouse-x.x.x.jar
```

**Requires:** Java 21+, Maven 3.8+

---

## 📄 License

MIT © [Zerep231](https://github.com/Zerep231)
