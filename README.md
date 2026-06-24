# ZeAuctionHouse

A lightweight auction house plugin for **Paper 1.21.1+** — no Vault, no virtual currency.

Players trade using real in-game items (Diamonds, Emeralds, etc.).

---

## Key Features

- **No Vault required** — economy runs on physical items, no plugins to depend on
- **Marketplace** — list, browse, buy, and cancel item listings via clean GUI
- **Builder Shop** — buy building materials (stone, wood, glass, plants, deco, nether, end) with quantity selector
- **Delivery Box** — items are stored safely; no item loss if inventory is full
- **Session persistence** — sell sessions survive server restarts and crashes
- **SQLite & MySQL** — choose your database; HikariCP connection pooling included
- **Multi-language** — English and Vietnamese built-in (`lang: en` or `lang: vi`)
- **Bedrock-friendly** — GUI works with Geyser/Floodgate players
- **Audit logging** — every transaction is logged to file and database

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/ah` | `zeah.use` | Browse listings |
| `/ah sell <price>` | `zeah.sell` | List held item for sale |
| `/ah claim` | `zeah.claim` | Collect items from mailbox |
| `/ah shop` | `zeah.shop` | Open builder shop |
| `/ah mylistings` | `zeah.use` | View/cancel your listings |
| `/ah reload` | `zeah.admin` | Reload config & shop files |

---

## Requirements

- Paper 1.21.1+
- Java 17+

## Installation

1. Drop `ZeAuctionHouse-x.x.x.jar` into `plugins/`
2. Restart server
3. Edit `plugins/ZeAuctionHouse/config.yml`

## Quick Config

```yaml
economy:
  currency-item: DIAMOND
  currency-name: Diamond

database:
  type: sqlite   # or mysql

lang: en         # or vi
```

---

## Version History

| Version | Notes |
|---|---|
| v3.3.1 | Bug fixes: session DB persistence, shop GUI quantity flow, delivery FAILED status, removed duplicate package |
| v3.3.0 | Initial stable release |
