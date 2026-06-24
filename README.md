# ZeAuctionHouse V3.3

> **Stable · Secure · Scalable** — Minecraft Paper 1.21.1+ Auction House Plugin

[![Build](https://github.com/Zerep231/Zeautionhouse/actions/workflows/build.yml/badge.svg)](https://github.com/Zerep231/Zeautionhouse/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Paper](https://img.shields.io/badge/Paper-1.21.1+-green.svg)](https://papermc.io)

---

## Features

| Feature | Status |
|---------|--------|
| ACID Transactions (buy/sell/cancel/expire) | ✅ |
| Session persistence (crash-safe pending states) | ✅ |
| Delivery system (PENDING → CLAIMING → CLAIMED) | ✅ |
| Async DB operations (no main-thread blocking) | ✅ |
| GUI click protection (drag/shift/hotkey blocked) | ✅ |
| Cache with TTL + immediate invalidation | ✅ |
| SQLite (default) + MySQL support | ✅ |
| Builder Shop (7 categories, configurable) | ✅ |
| Language system (en / vi, fully externalized) | ✅ |
| Audit logs with 30-day auto-rotation | ✅ |
| Item blacklist | ✅ |
| Vault economy integration | ✅ |
| Bedrock-friendly (left-click primary actions) | ✅ |
| Pagination (45 items/page) | ✅ |

---

## Requirements

- **Paper** 1.21.1+
- **Java** 21
- **Vault** + any economy plugin (EssentialsX Economy, CMI, etc.)

---

## Installation

1. Drop `ZeAuctionHouse-3.3.0.jar` into your `plugins/` folder.
2. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin.
3. Restart server. Edit `plugins/ZeAuctionHouse/config.yml`.
4. Reload with `/ah reload` (op only).

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/ah` | Open auction house (page 1) | `zeah.use` |
| `/ah browse [page]` | Browse listings | `zeah.use` |
| `/ah sell <price>` | Sell held item | `zeah.sell` |
| `/ah claim` | Claim all delivered items | `zeah.claim` |
| `/ah shop` | Open builder shop | `zeah.shop` |
| `/ah mylistings` | View & cancel your listings | `zeah.use` |
| `/ah reload` | Reload config + lang | `zeah.admin` |

**Aliases:** `/auctionhouse`, `/auction`

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `zeah.use` | Use the auction house | everyone |
| `zeah.sell` | List items for sale | everyone |
| `zeah.claim` | Claim delivered items | everyone |
| `zeah.shop` | Use builder shop | everyone |
| `zeah.admin` | Reload, admin commands | op |
| `zeah.bypass.limit` | Bypass listing limit | op |
| `zeah.bypass.blacklist` | Sell blacklisted items | op |

---

## Configuration

```yaml
# plugins/ZeAuctionHouse/config.yml

database:
  type: sqlite       # sqlite or mysql
  host: localhost
  port: 3306
  database: zeauctionhouse
  user: root
  password: ''
  pool-size: 10

cache:
  ttl-seconds: 10    # Cache refresh interval

session:
  timeout-minutes: 5 # Auto-abort inactive sell sessions

limits:
  max-listings-per-player: 10
  max-total-listings: 1000
  max-price: 999999.0
  min-price: 1.0
  listing-fee-percent: 0.0  # % fee deducted on list (0 = disabled)

expire:
  check-interval-minutes: 10
  default-days: 7            # Listings expire after N days

log:
  enabled: true
  rotation-days: 30

blacklist:
  - BEDROCK
  - COMMAND_BLOCK

shop:
  enabled: true
  categories: [stone, wood, glass, decoration, plants, nether, end]

lang: en   # en or vi
```

### MySQL Setup

```yaml
database:
  type: mysql
  host: 127.0.0.1
  port: 3306
  database: zeauctionhouse
  user: zeah
  password: your_password_here
  pool-size: 10
```

### Custom Language

Copy `plugins/ZeAuctionHouse/lang/en.yml` → `lang/custom.yml`, edit messages, set `lang: custom` in config.

---

## GUI Overview

### Main Auction House (`/ah`)
```
┌─────────────────────────────────────────────┐
│  [Item] [Item] [Item] ... (45 item slots)   │
│  [Item] [Item] [Item] ... 5 rows of 9       │
├─────────────────────────────────────────────┤
│ [MyList] [Claim] [ ← ] [ ✕ ] [ → ]  nav   │
└─────────────────────────────────────────────┘
```
- **Left-click** any item → opens Confirm Purchase dialog

### Confirm Purchase (27-slot)
```
[ ═══════════════════════════════════════════ ]
[  ✅ Confirm  ]  [ Item Preview ]  [ ❌ Back ]
[ ═══════════════════════════════════════════ ]
```

### Builder Shop
- Category overview → Item list per category
- Left-click = buy 1 | Shift-left = buy 16 | Right-click = buy 64

---

## Database Schema

```sql
listings   (id, seller_uuid, seller_name, item_data BLOB, price, status, created_at, expire_at)
deliveries (id, buyer_uuid,  buyer_name,  listing_id, item_data BLOB, status, reason, created_at)
audit_logs (id, type, actor_uuid, details, created_at)
```

**Delivery Status Flow:**
```
PENDING ──► CLAIMING ──► CLAIMED
  (row locked on CLAIMING to prevent duplicate claims)
```

---

## Architecture

```
AuctionHouse/src/main/java/dev/zerep/zeah/
├── database/
│   ├── DatabaseExecutor.java       # Interface with CompletableFuture API
│   ├── SQLiteExecutor.java         # Single-writer thread + WAL mode
│   └── MySQLExecutor.java          # HikariCP pooled connections
├── cache/
│   └── ListingCache.java           # TTL cache with RW-lock, per-page access
├── session/
│   ├── CreateSession.java          # State machine: STARTED → COMPLETED
│   └── SessionManager.java         # Timeout watcher + crash recovery
├── gui/
│   ├── AuctionGUI.java             # Base class: click protection, helpers
│   ├── MainAuctionGUI.java         # 45-slot paginated listing browser
│   ├── ConfirmBuyGUI.java          # Purchase confirmation (27-slot)
│   ├── ConfirmSellGUI.java         # Sell confirmation (27-slot)
│   ├── MyListingsGUI.java          # Cancel active listings
│   ├── ShopGUI.java                # Builder shop category chooser
│   └── ShopCategoryGUI.java        # Items per shop category
├── managers/
│   ├── AuctionManager.java         # Buy/sell/cancel/expire orchestration
│   ├── CacheManager.java           # Cache lifecycle management
│   ├── DeliveryManager.java        # Claim flow with inventory fallback
│   └── AuditLogger.java            # DB + file logging, 30-day rotation
├── shop/
│   ├── ShopManager.java            # Loads shop/*.yml categories
│   ├── ShopCategory.java
│   └── ShopItem.java
├── lang/
│   └── LangManager.java            # Loads lang files, fallback to en
├── commands/
│   └── AHCommand.java              # /ah command + tab completion
├── listeners/
│   ├── GUIListener.java            # Blocks all dangerous inventory actions
│   └── PlayerListener.java         # Join notify, quit session cleanup
└── ZeAuctionHouse.java             # Plugin main class, wires all components
```

---

## Building from Source

```bash
# Requires Java 21 + Maven 3.8+
git clone https://github.com/Zerep231/Zeautionhouse.git
cd Zeautionhouse/AuctionHouse
mvn clean package
# Output: target/ZeAuctionHouse-3.3.0.jar
```

---

## Changelog

### V3.3.0
- Full rewrite following V3.3 design specification
- ACID transaction safety on all write operations
- Session persistence with 5-minute timeout and crash recovery
- Delivery system with row-level locking (PENDING → CLAIMING → CLAIMED)
- Cache invalidation on every write event, 10-second TTL
- GUI: all dangerous click types blocked (shift, drag, hotkey, double-click)
- Bilingual support: English + Vietnamese (externalized lang files)
- Builder Shop with 7 configurable YAML categories
- Audit logging to DB + rotating log files (30-day retention)
- MySQL support with HikariCP, SQLite with WAL mode + single-writer
- Vault economy integration (listing fee, purchase deduction, seller payout)

---

## License

MIT © Zerep231
