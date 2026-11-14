#!/bin/bash
# ========================================================
# Script khởi chạy Hỗ Trợ Gia Đình (Go Native Desktop App)
# ========================================================
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_PATH="$PROJECT_DIR/desktop/family-remote"

if [ ! -f "$BIN_PATH" ]; then
    echo "Đang biên dịch ứng dụng Go Desktop..."
    cd "$PROJECT_DIR/desktop" && go build -ldflags="-s -w" -o family-remote
fi

# Chạy trực tiếp binary Go All-in-One (Server + Tunnel + GUI Window)
exec "$BIN_PATH" "$@"
