const assert = require('assert');
const http = require('http');
const WebSocket = require('ws');

async function testRelayFlow() {
    console.log('--- Bắt đầu Test Server & WebSocket Relay ---');
    
    // Import server file logic by spawning or creating a test instance
    const serverModule = require('./server.js');
    
    // Đợi server listen
    await new Promise(res => setTimeout(res, 500));

    const PORT = process.env.PORT || 8080;
    const wsUrl = `ws://127.0.0.1:${PORT}`;

    // 1. Test HTTP GET /
    await new Promise((resolve, reject) => {
        http.get(`http://127.0.0.1:${PORT}/`, (res) => {
            assert.strictEqual(res.statusCode, 200, 'HTTP status should be 200');
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                assert.ok(data.includes('Hỗ Trợ Bố Mẹ'), 'HTML content should match');
                console.log('✔ Test HTTP GET / OK');
                resolve();
            });
        }).on('error', reject);
    });

    // 2. Test Phone WebSocket & Web WebSocket Connection & Message relay
    const wsPhone = new WebSocket(wsUrl);
    const wsWeb = new WebSocket(wsUrl);

    await Promise.all([
        new Promise(res => wsPhone.on('open', res)),
        new Promise(res => wsWeb.on('open', res))
    ]);
    console.log('✔ Cả Phone và Web kết nối WebSocket thành công');

    // Web lắng nghe trạng thái kết nối
    let phoneConnectedNotified = false;
    let frameReceived = false;

    wsWeb.on('message', (msg) => {
        const payload = JSON.parse(msg.toString());
        if (payload.type === 'status' && payload.status === 'phone_connected') {
            phoneConnectedNotified = true;
        }
        if (payload.type === 'frame' && payload.image === 'test_base64_data') {
            frameReceived = true;
        }
    });

    // Register Web
    wsWeb.send(JSON.stringify({ type: 'register', role: 'web' }));
    // Register Phone
    wsPhone.send(JSON.stringify({ type: 'register', role: 'phone' }));

    await new Promise(res => setTimeout(res, 300));
    assert.ok(phoneConnectedNotified, 'Web nhận được thông báo phone_connected');
    console.log('✔ Phone đăng ký thành công và Web nhận được notification');

    // Phone gửi frame ảnh
    wsPhone.send(JSON.stringify({ type: 'frame', image: 'test_base64_data', width: 360, height: 640 }));
    await new Promise(res => setTimeout(res, 300));
    assert.ok(frameReceived, 'Web nhận được frame hình ảnh từ Phone');
    console.log('✔ Frame chuyển tiếp thành công từ Phone sang Web');

    // Web gửi lệnh tap tới Phone
    let tapReceived = false;
    wsPhone.on('message', (msg) => {
        const payload = JSON.parse(msg.toString());
        if (payload.type === 'tap' && payload.x === 0.5 && payload.y === 0.5) {
            tapReceived = true;
        }
    });

    wsWeb.send(JSON.stringify({ type: 'tap', x: 0.5, y: 0.5 }));
    await new Promise(res => setTimeout(res, 300));
    assert.ok(tapReceived, 'Phone nhận được lệnh tap từ Web');
    console.log('✔ Lệnh điều khiển tap chuyển tiếp thành công từ Web sang Phone');

    // Cleanup
    wsPhone.close();
    wsWeb.close();

    console.log('🎉 Toàn bộ test server relay thành công!');
    process.exit(0);
}

testRelayFlow().catch(err => {
    console.error('Test thất bại:', err);
    process.exit(1);
});
