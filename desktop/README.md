# Family Remote Desktop (Go Native)

Ứng dụng Desktop Native All-in-One viết bằng Go (Golang) cho máy con để điều khiển và hỗ trợ điện thoại Android của bố mẹ từ xa.

## 🚀 Đặc điểm nổi bật
- **Độc lập hoàn toàn:** Biên dịch thành 1 file nhị phân duy nhất (`family-remote`), không cần cài đặt Node.js hay môi trường npm.
- **Tích hợp All-in-One:**
  - Nhúng sẵn HTTP Server và WebSocket Server tốc độ cao (Go `gorilla/websocket`).
  - Tự động gọi và quản lý tiến trình `cloudflared tunnel` ngầm để hỗ trợ kết nối Internet/4G qua WSS (Cloudflare TLS).
  - Tự động thiết lập `adb reverse` nếu điện thoại đang cắm cáp USB.
  - Tự động mở cửa sổ Native Desktop UI không viền (Borderless App Mode).
- **Hỗ trợ đầy đủ tính năng:**
  - Tự động xoay màn hình (Auto Screen Rotation ngang/dọc).
  - Chuột trái để chạm (Tap), kéo chuột để vuốt (Swipe).
  - Phím điều hướng hệ thống (Back, Home, Recents, Notifications).
  - Phím cứng (Power, Lock, Volume +, Volume -, Mute).
  - Mở nhanh Cài đặt, Quyền ứng dụng, Wi-Fi và Dừng hỗ trợ.
  - Chụp ảnh màn hình lưu trực tiếp về máy.

## 🛠️ Biên dịch từ nguồn
Yêu cầu Go 1.20 trở lên:
```bash
cd desktop
go build -ldflags="-s -w" -o family-remote
```

## 💻 Cách khởi chạy
Chạy trực tiếp binary hoặc qua script:
```bash
./start_support.sh
```
Hoặc click trực tiếp vào icon ứng dụng **"Hỗ Trợ Gia Đình"** trên menu ứng dụng máy tính Linux.
