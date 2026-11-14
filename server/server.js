const WebSocket = require('ws');
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;

// Tạo HTTP Server phục vụ giao diện Web Controller cho con
const server = http.createServer((req, res) => {
    if (req.url === '/' || req.url === '/index.html') {
        const filePath = path.join(__dirname, 'index.html');
        fs.readFile(filePath, (err, html) => {
            if (err) {
                res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
                res.end('Lỗi máy chủ khi đọc index.html');
                return;
            }
            res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
            res.end(html);
        });
    } else {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('404 Not Found');
    }
});

// WebSocket Server kết nối Điện thoại & Web Browser
const wss = new WebSocket.Server({
    server,
    maxPayload: 10 * 1024 * 1024 // Tối đa 10MB cho mỗi message frame
});

let phoneClient = null;
const webClients = new Set();

// Thống kê lưu lượng và logging theo chu kỳ
let frameCount = 0;
let lastLogTime = Date.now();

const statsInterval = setInterval(() => {
    const now = Date.now();
    if (frameCount > 0) {
        const seconds = (now - lastLogTime) / 1000;
        const fps = (frameCount / seconds).toFixed(1);
        console.log(`[Stream Status] Đang truyền ${fps} FPS tới ${webClients.size} web viewer(s)`);
        frameCount = 0;
        lastLogTime = now;
    }
}, 5000);
if (statsInterval.unref) statsInterval.unref();

wss.on('connection', (ws, req) => {
    const ip = req ? req.socket.remoteAddress : 'unknown';
    console.log(`[Connect] Client mới kết nối từ ${ip}`);

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message.toString());

            // 1. Điện thoại đăng ký vai trò
            if (data.type === 'register' && data.role === 'phone') {
                if (phoneClient && phoneClient !== ws && phoneClient.readyState === WebSocket.OPEN) {
                    console.log('[Register] Phát hiện phiên điện thoại mới, đóng phiên cũ');
                    try {
                        phoneClient.close(1000, 'Replaced by new phone session');
                    } catch (_) {}
                }
                phoneClient = ws;
                console.log(`--> [Phone] Điện thoại bố mẹ đã sẵn sàng! (${data.screenWidth || '?'}x${data.screenHeight || '?'})`);
                broadcastToWeb({
                    type: 'status',
                    status: 'phone_connected',
                    screenWidth: data.screenWidth,
                    screenHeight: data.screenHeight
                });
                return;
            }

            // 2. Web viewer đăng ký
            if (data.type === 'register' && data.role === 'web') {
                webClients.add(ws);
                console.log(`--> [Web] Web Controller mới kết nối! Tổng số web viewer: ${webClients.size}`);
                if (phoneClient && phoneClient.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({ type: 'status', status: 'phone_connected' }));
                } else {
                    ws.send(JSON.stringify({ type: 'status', status: 'phone_disconnected' }));
                }
                return;
            }

            // 3. Nếu là frame hình ảnh từ điện thoại -> chuyển tiếp tới tất cả Web Controller
            if (data.type === 'frame') {
                frameCount++;
                broadcastToWeb(data);
                return;
            }

            // 4. Nếu là lệnh điều khiển từ Web -> gửi tới điện thoại
            const supportedActions = [
                'tap', 'swipe', 'back', 'home', 'recents',
                'notifications', 'power', 'lock', 'volume_up', 'volume_down', 'disconnect'
            ];

            if (supportedActions.includes(data.type)) {
                console.log(`[Command] Điều khiển: ${data.type} ${data.type === 'tap' ? `(${data.x?.toFixed(3)}, ${data.y?.toFixed(3)})` : ''}`);
                if (phoneClient && phoneClient.readyState === WebSocket.OPEN) {
                    phoneClient.send(JSON.stringify(data));
                } else {
                    console.warn(`[Command] Bỏ qua lệnh '${data.type}' vì điện thoại chưa kết nối`);
                }
                return;
            }

        } catch (e) {
            console.error('[Error] Lỗi phân tích cú pháp message:', e.message);
        }
    });

    const cleanupClient = () => {
        if (ws === phoneClient) {
            console.log('[Disconnect] Điện thoại bố mẹ đã ngắt kết nối');
            phoneClient = null;
            broadcastToWeb({ type: 'status', status: 'phone_disconnected' });
        } else if (webClients.has(ws)) {
            webClients.delete(ws);
            console.log(`[Disconnect] Một web controller đã rời đi. Còn lại: ${webClients.size}`);
        }
    };

    ws.on('close', (code, reason) => {
        cleanupClient();
    });

    ws.on('error', (err) => {
        console.error('[Socket Error]:', err.message);
        cleanupClient();
    });
});

function broadcastToWeb(data) {
    if (webClients.size === 0) return;
    const payload = JSON.stringify(data);
    for (const client of webClients) {
        if (client.readyState === WebSocket.OPEN) {
            client.send(payload, (err) => {
                if (err) {
                    console.error('[Broadcast Error]:', err.message);
                }
            });
        }
    }
}

server.listen(PORT, '0.0.0.0', () => {
    console.log(`=======================================================`);
    console.log(`Server hỗ trợ từ xa đang chạy tại: http://localhost:${PORT}`);
    console.log(`WebSocket URL cho điện thoại: ws://<IP_MÁY_BẠN>:${PORT}`);
    console.log(`=======================================================`);
});

module.exports = { server, wss };
