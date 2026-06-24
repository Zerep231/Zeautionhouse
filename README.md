# ZeAuctionHouse V3.3

> **Nhà đấu giá dùng khoáng sản làm tiền tệ** — Không cần Vault, không cần plugin kinh tế.

[![Paper](https://img.shields.io/badge/Paper-1.21.1+-brightgreen)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Build](https://github.com/Zerep231/Zeautionhouse/actions/workflows/build.yml/badge.svg)](https://github.com/Zerep231/Zeautionhouse/actions)

---

## Tại sao dùng ZeAuctionHouse?

| Điểm nổi bật | Chi tiết |
|---|---|
| **Không cần Vault** | Dùng item vanilla làm tiền tệ (mặc định: Diamond). Cấu hình được theo server |
| **ACID transactions** | Mọi giao dịch (mua/bán/hủy/hết hạn) đều atomic — không bao giờ mất item hay trùng lặp |
| **Delivery system** | `PENDING → CLAIMING → CLAIMED` với row-level lock — người mua luôn nhận được hàng |
| **Session persistence** | Crash-safe: phiên đăng bán tự khôi phục sau khi server restart |
| **Async 100%** | Không block main thread — zero lag ngay cả ở server đông |
| **GUI click protection** | Chặn shift-click, drag, hotkey, double-click — không bao giờ mất item qua GUI |
| **SQLite & MySQL** | SQLite WAL mode cho server nhỏ; MySQL HikariCP cho server lớn |
| **Builder Shop** | 7 danh mục vật liệu riêng file YAML — dễ cấu hình, dễ mở rộng |
| **Đa ngôn ngữ** | Tiếng Anh & Tiếng Việt, fallback tự động. Thêm ngôn ngữ mới dễ dàng |
| **Audit logs** | Ghi log vào DB + file theo ngày, tự xóa sau 30 ngày |

---

## Yêu cầu

- **Paper** 1.21.1+
- **Java** 17+
- ❌ ~~Vault~~ — không cần

---

## Cài đặt

1. Tải `ZeAuctionHouse-3.3.0.jar` từ [Releases](https://github.com/Zerep231/Zeautionhouse/releases/latest)
2. Bỏ vào thư mục `plugins/`
3. Khởi động lại server
4. Chỉnh `plugins/ZeAuctionHouse/config.yml`
5. Dùng `/ah reload` để áp dụng thay đổi

---

## Lệnh

| Lệnh | Mô tả | Quyền |
|---|---|---|
| `/ah` | Mở nhà đấu giá | `zeah.use` |
| `/ah sell <số lượng>` | Đăng bán item đang cầm | `zeah.sell` |
| `/ah claim` | Nhận item từ hộp thư | `zeah.claim` |
| `/ah shop` | Mở cửa hàng vật liệu | `zeah.shop` |
| `/ah mylistings` | Xem & hủy tin đăng của mình | `zeah.use` |
| `/ah reload` | Tải lại cấu hình | `zeah.admin` |

**Alias:** `/auctionhouse`, `/auction`

> **Ví dụ:** `/ah sell 5` → đăng bán item đang cầm với giá 5 Diamond

---

## Cấu hình chính

```yaml
# plugins/ZeAuctionHouse/config.yml

economy:
  currency-item: DIAMOND    # Item dùng làm tiền tệ
  currency-name: Diamond    # Tên hiển thị

database:
  type: sqlite              # sqlite | mysql

limits:
  max-listings-per-player: 10
  max-price: 9999
  min-price: 1

expire:
  default-days: 7           # Tin đăng tự hết hạn sau N ngày

lang: vi                    # en | vi
```

### Chuyển sang MySQL

```yaml
database:
  type: mysql
  host: 127.0.0.1
  port: 3306
  database: zeauctionhouse
  user: zeah
  password: matkhau
  pool-size: 10
```

### Thêm ngôn ngữ mới

Chép `plugins/ZeAuctionHouse/lang/en.yml` → `lang/custom.yml`, sửa nội dung, đặt `lang: custom` trong config.

---

## Shop categories

Mỗi danh mục là một file YAML riêng trong `plugins/ZeAuctionHouse/shop/`:

```
shop/
├── stone.yml        ← Đá các loại
├── wood.yml         ← Gỗ các loại
├── glass.yml        ← Kính & kính màu
├── decoration.yml   ← Vật liệu trang trí
├── plants.yml       ← Cây cối & hoa
├── nether.yml       ← Vật liệu Nether
└── end.yml          ← Vật liệu End
```

Ví dụ `shop/stone.yml`:
```yaml
name: "Stone"
icon: STONE
items:
  - material: STONE
    price: 1
  - material: COBBLESTONE
    price: 1
  - material: GRANITE
    price: 2
```

---

## Luồng giao dịch

```
Người bán:  /ah sell 5  →  [Confirm GUI]  →  Item bị lấy, tin đăng lên sàn
Người mua:  Click item  →  [Confirm GUI]  →  5 Diamond bị trừ, item vào hộp thư
Seller nhận tiền:  Online → nhận ngay  |  Offline → vào hộp thư, /ah claim
```

**Delivery flow:**
```
PENDING ──► CLAIMING ──► CLAIMED
              ↑ row-level lock: chống nhận trùng 2 lần
```

---

## Build từ source

```bash
# Yêu cầu Java 21 + Maven 3.8+
git clone https://github.com/Zerep231/Zeautionhouse.git
cd Zeautionhouse/AuctionHouse
mvn clean package
# Output: target/ZeAuctionHouse-3.3.0.jar
```

---

## Changelog

### V3.3.0
- Hệ thống economy vanilla: dùng item làm tiền, không cần Vault
- ACID transactions trên toàn bộ thao tác ghi DB
- Session persistence với crash recovery
- Delivery system với row-level lock (PENDING → CLAIMING → CLAIMED)
- Cache invalidation tức thì + TTL 10 giây
- GUI: chặn toàn bộ click nguy hiểm
- Hỗ trợ song ngữ Anh/Việt
- Builder Shop: 7 file YAML riêng biệt, cấu hình độc lập
- Audit log vào DB + file xoay vòng 30 ngày
- SQLite (WAL) + MySQL (HikariCP)

---

## License

MIT © Zerep231
