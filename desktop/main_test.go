package main

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

func TestDesktopRelayFlow(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/", handleHTTP)
	mux.HandleFunc("/ws", handleWebSocket)

	ts := httptest.NewServer(mux)
	defer ts.Close()

	// 1. Test HTTP GET /
	resp, err := http.Get(ts.URL)
	if err != nil {
		t.Fatalf("Lỗi GET /: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("Status code khác 200: %d", resp.StatusCode)
	}

	// 2. Test WebSocket connection
	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"

	wsPhone, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("Phone dial error: %v", err)
	}
	defer wsPhone.Close()

	wsWeb, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("Web dial error: %v", err)
	}
	defer wsWeb.Close()

	// Đăng ký vai trò Phone trước và chờ đăng ký xong
	phoneReg := map[string]interface{}{
		"type":         "register",
		"role":         "phone",
		"screenWidth":  1080,
		"screenHeight": 2400,
		"orientation":  "portrait",
	}
	wsPhone.WriteJSON(phoneReg)
	time.Sleep(50 * time.Millisecond)

	// Đăng ký Web sau
	webReg := map[string]interface{}{
		"type": "register",
		"role": "web",
	}
	wsWeb.WriteJSON(webReg)

	// Web nhận status phone_connected
	var statusMsg map[string]interface{}
	wsWeb.SetReadDeadline(time.Now().Add(2 * time.Second))
	if err := wsWeb.ReadJSON(&statusMsg); err != nil {
		t.Fatalf("Web không nhận được status: %v", err)
	}
	if statusMsg["status"] != "phone_connected" {
		t.Fatalf("Status mong muốn phone_connected nhưng nhận: %v", statusMsg["status"])
	}

	// 3. Test gửi frame từ Phone sang Web
	frameMsg := map[string]interface{}{
		"type":   "frame",
		"image":  "base64_test_frame",
		"width":  540,
		"height": 1200,
	}
	wsPhone.WriteJSON(frameMsg)

	var receivedFrame map[string]interface{}
	wsWeb.SetReadDeadline(time.Now().Add(2 * time.Second))
	if err := wsWeb.ReadJSON(&receivedFrame); err != nil {
		t.Fatalf("Web không nhận được frame: %v", err)
	}
	if receivedFrame["image"] != "base64_test_frame" {
		t.Fatalf("Frame content không khớp: %v", receivedFrame)
	}

	// 4. Test gửi lệnh tap từ Web sang Phone
	tapCmd := map[string]interface{}{
		"type": "tap",
		"x":    0.5,
		"y":    0.7,
	}
	wsWeb.WriteJSON(tapCmd)

	var receivedCmd map[string]interface{}
	wsPhone.SetReadDeadline(time.Now().Add(2 * time.Second))
	if err := wsPhone.ReadJSON(&receivedCmd); err != nil {
		t.Fatalf("Phone không nhận được tap cmd: %v", err)
	}
	if receivedCmd["type"] != "tap" || receivedCmd["x"].(float64) != 0.5 {
		t.Fatalf("Tap cmd không khớp: %v", receivedCmd)
	}

	t.Log("✔ Toàn bộ luồng Go Desktop Native Server & WebSocket Relay hoạt động hoàn hảo!")
}
