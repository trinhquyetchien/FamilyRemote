package main

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"regexp"
	"sync"
	"syscall"
	"time"

	"github.com/gorilla/websocket"
)

//go:embed web/index.html
var indexHTML []byte

var (
	upgrader = websocket.Upgrader{
		CheckOrigin: func(r *http.Request) bool { return true },
		ReadBufferSize:  10 * 1024 * 1024,
		WriteBufferSize: 10 * 1024 * 1024,
	}

	phoneMu     sync.Mutex
	phoneConn   *websocket.Conn
	phoneInfo   map[string]interface{}

	webMu       sync.Mutex
	webClients  = make(map[*websocket.Conn]bool)

	tunnelURL   string
	tunnelMu    sync.RWMutex
)

func setTunnelURL(url string) {
	tunnelMu.Lock()
	tunnelURL = url
	tunnelMu.Unlock()
}

func getTunnelURL() string {
	tunnelMu.RLock()
	defer tunnelMu.RUnlock()
	return tunnelURL
}

func broadcastToWeb(message []byte) {
	webMu.Lock()
	defer webMu.Unlock()
	for client := range webClients {
		if err := client.WriteMessage(websocket.TextMessage, message); err != nil {
			log.Printf("[Relay] Lỗi gửi tới Web viewer: %v", err)
			client.Close()
			delete(webClients, client)
		}
	}
}

func handleHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Header.Get("Upgrade") == "websocket" {
		handleWebSocket(w, r)
		return
	}
	switch r.URL.Path {
	case "/", "/index.html":
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.WriteHeader(http.StatusOK)
		w.Write(indexHTML)
	case "/api/info":
		w.Header().Set("Content-Type", "application/json")
		info := map[string]interface{}{
			"tunnel_url": getTunnelURL(),
			"local_ws":   "ws://127.0.0.1:8080/ws",
			"has_phone":  phoneConn != nil,
		}
		json.NewEncoder(w).Encode(info)
	default:
		http.NotFound(w, r)
	}
}

func handleWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[WS Upgrade Error]: %v", err)
		return
	}
	defer conn.Close()

	for {
		_, msg, err := conn.ReadMessage()
		if err != nil {
			break
		}

		var payload map[string]interface{}
		if err := json.Unmarshal(msg, &payload); err != nil {
			continue
		}

		msgType, _ := payload["type"].(string)

		// 1. Điện thoại đăng ký
		if msgType == "register" && payload["role"] == "phone" {
			phoneMu.Lock()
			if phoneConn != nil && phoneConn != conn {
				phoneConn.Close()
			}
			phoneConn = conn
			phoneInfo = payload
			phoneMu.Unlock()

			log.Printf("--> [Phone Connected] Điện thoại bố mẹ đã sẵn sàng! (%v x %v, %v)",
				payload["screenWidth"], payload["screenHeight"], payload["orientation"])

			statusMsg, _ := json.Marshal(map[string]interface{}{
				"type":         "status",
				"status":       "phone_connected",
				"screenWidth":  payload["screenWidth"],
				"screenHeight": payload["screenHeight"],
				"orientation":  payload["orientation"],
			})
			broadcastToWeb(statusMsg)
			continue
		}

		// 2. Web Controller đăng ký
		if msgType == "register" && payload["role"] == "web" {
			webMu.Lock()
			webClients[conn] = true
			webMu.Unlock()

			phoneMu.Lock()
			hasPhone := phoneConn != nil
			pInfo := phoneInfo
			phoneMu.Unlock()

			if hasPhone {
				statusMsg, _ := json.Marshal(map[string]interface{}{
					"type":         "status",
					"status":       "phone_connected",
					"screenWidth":  pInfo["screenWidth"],
					"screenHeight": pInfo["screenHeight"],
					"orientation":  pInfo["orientation"],
				})
				conn.WriteMessage(websocket.TextMessage, statusMsg)
			} else {
				statusMsg, _ := json.Marshal(map[string]interface{}{
					"type":   "status",
					"status": "phone_disconnected",
				})
				conn.WriteMessage(websocket.TextMessage, statusMsg)
			}
			continue
		}

		// 3. Sự kiện xoay màn hình từ điện thoại
		if msgType == "screen_size" {
			phoneMu.Lock()
			if phoneInfo != nil {
				phoneInfo["screenWidth"] = payload["width"]
				phoneInfo["screenHeight"] = payload["height"]
				phoneInfo["orientation"] = payload["orientation"]
			}
			phoneMu.Unlock()
			broadcastToWeb(msg)
			continue
		}

		// 4. Frame hình ảnh truyền từ điện thoại -> chuyển tiếp tới Web viewer
		if msgType == "frame" {
			broadcastToWeb(msg)
			continue
		}

		// 5. Lệnh điều khiển gửi từ Desktop -> Điện thoại
		switch msgType {
		case "tap", "swipe", "back", "home", "recents", "notifications",
			"power", "lock", "volume_up", "volume_down", "mute",
			"open_settings", "open_app_permissions", "open_accessibility", "open_wifi",
			"disconnect":
			phoneMu.Lock()
			if phoneConn != nil {
				if err := phoneConn.WriteMessage(websocket.TextMessage, msg); err != nil {
					log.Printf("[Command Send Error]: %v", err)
				}
			}
			phoneMu.Unlock()
		}
	}

	// Dọn dẹp kết nối khi thoát
	phoneMu.Lock()
	if conn == phoneConn {
		phoneConn = nil
		phoneInfo = nil
		log.Println("[Phone Disconnected] Điện thoại bố mẹ đã ngắt kết nối")
		statusMsg, _ := json.Marshal(map[string]interface{}{
			"type":   "status",
			"status": "phone_disconnected",
		})
		broadcastToWeb(statusMsg)
	}
	phoneMu.Unlock()

	webMu.Lock()
	delete(webClients, conn)
	webMu.Unlock()
}

func startCloudflaredTunnel(port int) *exec.Cmd {
	cmdPath, err := exec.LookPath("cloudflared")
	if err != nil {
		// Kiểm tra thêm tại ~/.local/bin
		homeDir, _ := os.UserHomeDir()
		localBin := homeDir + "/.local/bin/cloudflared"
		if _, err := os.Stat(localBin); err == nil {
			cmdPath = localBin
		} else {
			log.Println("[Tunnel] Không tìm thấy lệnh 'cloudflared', bỏ qua tunnel ngầm")
			return nil
		}
	}

	log.Printf("[Tunnel] Đang khởi chạy Cloudflare Tunnel cho cổng %d...", port)
	cmd := exec.Command(cmdPath, "tunnel", "--url", fmt.Sprintf("http://localhost:%d", port))

	stderrPipe, err := cmd.StderrPipe()
	if err != nil {
		log.Printf("[Tunnel Pipe Error]: %v", err)
		return nil
	}

	if err := cmd.Start(); err != nil {
		log.Printf("[Tunnel Start Error]: %v", err)
		return nil
	}

	go func() {
		buf := make([]byte, 2048)
		re := regexp.MustCompile(`https://[a-zA-Z0-9-]+\.trycloudflare\.com`)
		for {
			n, err := stderrPipe.Read(buf)
			if n > 0 {
				output := string(buf[:n])
				match := re.FindString(output)
				if match != "" {
					setTunnelURL(match)
					log.Printf("=======================================================")
					log.Printf("🎉 CLOUDFLARE TUNNEL URL: %s", match)
					log.Printf("WebSocket URL cho điện thoại: %s", regexp.MustCompile(`^https://`).ReplaceAllString(match, "wss://"))
					log.Printf("=======================================================")
					break
				}
			}
			if err != nil {
				break
			}
		}
	}()

	return cmd
}

func launchAppWindow(targetURL string) *exec.Cmd {
	// Tìm trình duyệt hỗ trợ flag --app (Chạy cửa sổ Native Borderless Desktop App)
	browsers := []string{"google-chrome", "google-chrome-stable", "chromium-browser", "chromium", "brave"}
	for _, b := range browsers {
		if path, err := exec.LookPath(b); err == nil {
			args := []string{
				fmt.Sprintf("--app=%s", targetURL),
				"--window-size=1200,900",
				"--class=family-remote",
				"--user-data-dir=" + os.TempDir() + "/family_remote_profile",
			}
			cmd := exec.Command(path, args...)
			if err := cmd.Start(); err == nil {
				log.Printf("[Window] Đã mở cửa sổ ứng dụng qua %s", b)
				return cmd
			}
		}
	}

	// Fallback mở bằng xdg-open nếu không có Chrome/Chromium
	if xdg, err := exec.LookPath("xdg-open"); err == nil {
		cmd := exec.Command(xdg, targetURL)
		cmd.Start()
		return cmd
	}

	return nil
}

func main() {
	port := 8080
	// Kiểm tra xem cổng 8080 có bị chiếm không
	l, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		log.Printf("Cổng %d đang bận, thử cổng khác: %v", port, err)
		l, err = net.Listen("tcp", ":0")
		if err != nil {
			log.Fatalf("Không thể mở cổng server: %v", err)
		}
		port = l.Addr().(*net.TCPAddr).Port
	}
	l.Close()

	mux := http.NewServeMux()
	mux.HandleFunc("/", handleHTTP)
	mux.HandleFunc("/ws", handleWebSocket)

	server := &http.Server{
		Addr:    fmt.Sprintf(":%d", port),
		Handler: mux,
	}

	go func() {
		log.Printf("=======================================================")
		log.Printf("Family Remote Desktop Native Server đang chạy tại :%d", port)
		log.Printf("=======================================================")
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Lỗi server HTTP/WebSocket: %v", err)
		}
	}()

	// Thiết lập adb reverse nếu có adb cắm dây
	go func() {
		if adbPath, err := exec.LookPath("adb"); err == nil {
			exec.Command(adbPath, "reverse", fmt.Sprintf("tcp:%d", port), fmt.Sprintf("tcp:%d", port)).Run()
		}
	}()

	// Khởi động Cloudflare Tunnel ngầm tự động
	tunnelCmd := startCloudflaredTunnel(port)

	// Chờ server sẵn sàng 300ms rồi mở cửa sổ ứng dụng
	time.Sleep(300 * time.Millisecond)
	localURL := fmt.Sprintf("http://127.0.0.1:%d", port)
	windowCmd := launchAppWindow(localURL)

	// Lắng nghe tín hiệu tắt OS (Ctrl+C, SIGTERM)
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

	// Đợi đóng cửa sổ ứng dụng hoặc tín hiệu tắt
	done := make(chan bool)
	if windowCmd != nil {
		go func() {
			windowCmd.Wait()
			done <- true
		}()
	}

	select {
	case <-sigChan:
		log.Println("[Exit] Nhận tín hiệu dừng từ hệ điều hành...")
	case <-done:
		log.Println("[Exit] Cửa sổ ứng dụng đã được đóng...")
	}

	// Dọn dẹp tunnel và server
	if tunnelCmd != nil && tunnelCmd.Process != nil {
		tunnelCmd.Process.Kill()
	}
	server.Close()
	log.Println("[Exit] Đã dọn dẹp tài nguyên và tắt ứng dụng hoàn tất.")
}
