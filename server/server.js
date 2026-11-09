const WebSocket = require('ws');
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;

// Tạo HTTP Server phục vụ giao diện Web Controller cho con
const server = http.createServer((req, res) => {
    if (req.url === '/' || req.url === '/index.html') {
        const html = fs.readFileSync(path.join(__dirname, 'index.html'));
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(html);
    } else {
        res.writeHead(404);
        res.end();
    }
});

// WebSocket Server kết nối Điện thoại & Web Browser
const wss = new WebSocket.Server({ server });

let phoneClient = null;
const webClients = new Set();

wss.on('connection', (ws) => {
    console.log('Một client mới đã kết nối');

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message.toString());

            // Điện thoại đăng ký vai trò
            if (data.type === 'register' && data.role === 'phone') {
                phoneClient = ws;
                console.log('--> Điện thoại bố mẹ đã sẵn sàng!');
                // Thông báo cho các web client
                broadcastToWeb({ type: 'status', status: 'phone_connected' });
                return;
            }

            // Web viewer đăng ký
            if (data.type === 'register' && data.role === 'web') {
                webClients.add(ws);
                console.log('--> Web Controller (con) đã kết nối!');
                if (phoneClient && phoneClient.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({ type: 'status', status: 'phone_connected' }));
                }
                return;
            }

            // Nếu là frame hình ảnh từ điện thoại -> chuyển tiếp tới Web Controller
            if (data.type === 'frame') {
                broadcastToWeb(data);
                return;
            }

            // Nếu là lệnh điều khiển từ Web -> gửi tới điện thoại
            if (['tap', 'swipe', 'back', 'home', 'recents', 'notifications', 'power', 'lock', 'volume_up', 'volume_down', 'disconnect'].includes(data.type)) {
                if (phoneClient && phoneClient.readyState === WebSocket.OPEN) {
                    phoneClient.send(JSON.stringify(data));
                }
                return;
            }

        } catch (e) {
            console.error('Lỗi phân tích cú pháp message:', e.message);
        }
    });

    ws.on('close', () => {
        if (ws === phoneClient) {
            console.log('Điện thoại bố mẹ đã ngắt kết nối');
            phoneClient = null;
            broadcastToWeb({ type: 'status', status: 'phone_disconnected' });
        } else {
            webClients.delete(ws);
        }
    });
});

function broadcastToWeb(data) {
    const payload = JSON.stringify(data);
    for (const client of webClients) {
        if (client.readyState === WebSocket.OPEN) {
            client.send(payload);
        }
    }
}

server.listen(PORT, '0.0.0.0', () => {
    console.log(`=======================================================`);
    console.log(`Server hỗ trợ từ xa đang chạy tại: http://localhost:${PORT}`);
    console.log(`WebSocket URL cho điện thoại: ws://<IP_MÁY_BẠN>:${PORT}`);
    console.log(`=======================================================`);
});
