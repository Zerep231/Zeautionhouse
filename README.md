<div align="center">

# 🏷️ ZeAuctionHouse

**Chợ giao dịch vật phẩm vanilla cho Paper 1.21+ · Hỗ trợ Geyser/Bedrock**

[![Version](https://img.shields.io/badge/version-3.3-gold?style=flat-square)](https://github.com/Zerep231/Zeautionhouse/releases/latest)
[![Paper](https://img.shields.io/badge/Paper-1.21.1+-f96854?style=flat-square)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square)](https://adoptium.net)
[![Geyser](https://img.shields.io/badge/Geyser-supported-00b4d8?style=flat-square)](https://geysermc.org)

### ⬇️ [Tải bản mới nhất tại đây →](https://github.com/Zerep231/Zeautionhouse/releases/latest)

</div>

---

## ✨ Tính năng

- 🛒 **Chợ player** — mua bán bất kỳ item nào bằng nguyên liệu vanilla (Kim cương, Emerald…)
- 📦 **Delivery Box** — item mua được giữ an toàn cho đến khi người chơi nhận
- 🏪 **Build Shop** — shop danh mục do server quản lý (blocks, decoration…)
- 🔒 **Anti-dupe** — chặn toàn bộ vector dupe GUI đã biết
- ⚛️ **Giao dịch atomic** — không thể mua trùng, không mất item khi server crash
- 📋 **Audit log** — mọi giao dịch đều được ghi lại trong database
- 📱 **Bedrock/Geyser** — GUI hoạt động đầy đủ + lệnh `/ah sell <price>` cho mobile
- ♻️ **Auto-expire** — listing hết hạn tự động trả item về tay người bán

---

## 🚀 Cài đặt

1. **[Tải file `.jar`](https://github.com/Zerep231/Zeautionhouse/releases/latest)**
2. Bỏ vào thư mục `plugins/` của server
3. Khởi động lại server — config tự tạo
4. Chỉnh `plugins/AuctionHouse/config.yml` theo ý muốn
5. `/ah reload` để áp dụng thay đổi config

> **Yêu cầu:** Paper 1.21.1+, Java 21+
> **Tùy chọn:** [Geyser](https://geysermc.org) + [Floodgate](https://github.com/GeyserMC/Floodgate) (để hỗ trợ Bedrock)

---

## 🎮 Lệnh & Quyền

| Lệnh | Mô tả |
|---|---|
| `/ah` | Mở chợ Browse |
| `/ah sell` | Mở GUI bán đồ (kéo item → đặt giá → xác nhận) |
| `/ah sell <giá> [loại tiền]` | Bán nhanh item trên tay, không cần GUI *(cho Bedrock)* |
| `/ah mine` | Xem và hủy listing của bạn |
| `/ah deliveries` | Mở Delivery Box nhận đồ và tiền |
| `/ah shop` | Mở Build Shop |
| `/ah reload` | Reload config *(cần quyền admin)* |

| Quyền | Mặc định | Mô tả |
|---|---|---|
| `ah.use` | Tất cả | Dùng tất cả lệnh player |
| `ah.admin` | OP | Dùng `/ah reload` |
| `ah.bypass-limit` | OP | Đăng nhiều hơn giới hạn `max-per-player` |

---

## ⚙️ Config chính

```yaml
max-per-player: 10          # Số listing tối đa mỗi người
listing-expire-hours: 72    # Listing hết hạn sau bao nhiêu giờ
broadcast-sales: true       # Thông báo toàn server khi có giao dịch
max-price: 999999           # Giá tối đa

currencies:
  - DIAMOND
  - EMERALD
  - NETHERITE_INGOT
  - GOLD_INGOT
  - IRON_INGOT
```

---

## 📜 Changelog

### v3.3
- **Fix P0-1:** Item bị trả lại giữa bước CREATE_1 → CREATE_2 (transitioning flag)
- **Fix P0-3:** Thứ tự claim đảo ngược — give item trước, xóa DB sau (an toàn hơn khi crash)
- **Fix P0-4:** `cancelListing` kiểm tra kết quả update trước khi insert delivery
- **Fix P0-5:** Pagination luôn về page 0 — `AhHolder` giờ lưu page trực tiếp
- **Fix P1-5:** Delivery GUI có pagination (PREV/NEXT) cho >45 delivery
- **Fix P2-1:** max-price được validate cả ở bước confirm GUI

### v3.2.2
- Fix: off-hand slot bị bỏ qua khi đếm currency (exploit kinh tế)

### v3.2.1
- Fix: quick-sell trả item về nếu DB lỗi
- Fix: text hướng dẫn Step-1 GUI

### v3.2.0
- Rewrite lớn: GuiManager, TransactionManager, SessionManager, anti-dupe, slot maps, cache, audit log, Adventure API, Bedrock support

---

<div align="center">
<sub>Made by <a href="https://github.com/Zerep231">Zerep</a> · Paper 1.21.1+ · No external plugin dependencies</sub>
</div>
