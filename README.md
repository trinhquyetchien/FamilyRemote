# Family Remote (Hỗ Trợ Gia Đình)

Ứng dụng hỗ trợ điều khiển màn hình điện thoại Android của bố mẹ từ xa thông qua trình duyệt Laptop/Điện thoại.

## ✨ Tính năng chính

- **Dành cho bố mẹ (1 chạm tối giản):**
  - Mở ứng dụng chỉ có đúng 1 nút tròn lớn: **[ BẬT HỖ TRỢ ]** / **[ TẮT HỖ TRỢ ]**.
  - Không có menu hay cấu hình phức tạp.
- **Dành cho con (Web Viewer như Android Studio Emulator):**
  - Nhấp chuột (Click) để chạm (Tap).
  - Kéo chuột (Drag) để vuốt lướt màn hình (Swipe).
  - Cụm phím điều hướng hệ thống: **Back**, **Home**, **Đa nhiệm (Recents)**, **Thanh thông báo**.
  - Cụm phím cứng vật lý: **Phím Nguồn**, **Khóa màn hình**, **Tăng / Giảm âm lượng**.
  - Nút **"DỪNG & TẮT PHIÊN TRÊN MÁY BỐ MẸ"**: Con chủ động tắt kết nối từ xa khi sửa xong.

## 🛠️ Cấu trúc dự án

- `app/`: Ứng dụng Android Native (Kotlin + Jetpack Compose + `MediaProjection` + `AccessibilityService`).
- `server/`: Server chuyển tiếp WebSocket & Giao diện Web Controller (HTML5 / Node.js).

## 🚀 Hướng dẫn cài đặt & sử dụng

### 1. Khởi động Relay Server trên Laptop
```bash
cd server
npm install
npm start
```
Server sẽ chạy tại: `http://localhost:8080`.

### 2. Cài đặt trên điện thoại bố mẹ
- Tải file APK đã build từ mục Releases hoặc build bằng lệnh:
```bash
./gradlew assembleDebug
```
File APK sinh ra tại: `app/build/outputs/apk/debug/app-debug.apk`.
- Cài đặt APK vào máy bố mẹ.
- Cấp quyền **Hỗ trợ tiếp cận (Accessibility Service)** cho app 1 lần duy nhất lúc cài đặt.
- Cấu hình địa chỉ WebSocket của con (ví dụ: `ws://<IP_MAY_CON>:8080`).

### 3. Thao tác điều khiển
- Bố mẹ mở app -> Bấm **[ BẬT HỖ TRỢ ]** -> Chọn **Bắt đầu ngay**.
- Con mở trình duyệt vào `http://localhost:8080` để thao tác chuột và phím cứng.
