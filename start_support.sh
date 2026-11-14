#!/bin/bash
# ========================================================
# Script khởi chạy Hỗ Trợ Gia Đình (Family Remote Laptop)
# ========================================================
PROJECT_DIR="/home/trinhquyetchien/Projects/Main/Personal/FamilyAndroid"
SERVER_DIR="$PROJECT_DIR/server"
LOG_DIR="$HOME/.local/share/family_remote"
mkdir -p "$LOG_DIR"

echo "Đang khởi động Relay Server..."
# Tắt tiến trình server cũ nếu còn chạy
pkill -f "node.*server.js" 2>/dev/null || true

# Chạy server ở nền
cd "$SERVER_DIR"
node server.js > "$LOG_DIR/server.log" 2>&1 &
SERVER_PID=$!

# Chờ server lắng nghe cổng 8080
sleep 1

# Thiết lập adb reverse nếu có điện thoại cắm USB
if which adb >/dev/null 2>&1; then
    adb reverse tcp:8080 tcp:8080 2>/dev/null || true
fi

echo "Khởi chạy Cloudflare Tunnel..."
pkill -f "cloudflared.*8080" 2>/dev/null || true
cloudflared tunnel --url http://localhost:8080 > "$LOG_DIR/tunnel.log" 2>&1 &
TUNNEL_PID=$!

# Tìm URL Cloudflare
TUNNEL_URL=""
for i in {1..30}; do
    if [ -f "$LOG_DIR/tunnel.log" ]; then
        TUNNEL_URL=$(grep -oE "https://[a-zA-Z0-9-]+\.trycloudflare\.com" "$LOG_DIR/tunnel.log" | head -n 1)
        if [ -n "$TUNNEL_URL" ]; then
            break
        fi
    fi
    sleep 0.5
done

TARGET_URL="${TUNNEL_URL:-http://localhost:8080}"
echo "--------------------------------------------------------"
echo "Web Controller URL: $TARGET_URL"
echo "--------------------------------------------------------"

# Tự động mở trình duyệt web
if which google-chrome >/dev/null 2>&1; then
    google-chrome "$TARGET_URL" >/dev/null 2>&1 &
elif which xdg-open >/dev/null 2>&1; then
    xdg-open "$TARGET_URL" >/dev/null 2>&1 &
fi

# Giữ script chạy để khi tắt terminal thì dọn dẹp tiến trình
trap "kill $SERVER_PID $TUNNEL_PID 2>/dev/null; exit" SIGINT SIGTERM EXIT
wait $SERVER_PID
