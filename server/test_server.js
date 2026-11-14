const assert = require('assert');
const http = require('http');
const WebSocket = require('ws');

async function runTestSuite() {
    console.log('=== BẮT ĐẦU TEST TOÀN DIỆN WEB SERVER & RELAY (AUTO ROTATE & MODERN ACTIONS) ===');
    
    const TEST_PORT = 9092;
    process.env.PORT = TEST_PORT;

    const { server, wss } = require('./server.js');
    const baseUrl = `http://127.0.0.1:${TEST_PORT}`;
    const wsUrl = `ws://127.0.0.1:${TEST_PORT}`;

    await new Promise(r => setTimeout(r, 600));

    // Test 1: Kiểm tra HTTP GET trang chủ
    console.log('\n--- 1. Kiểm tra HTTP GET / và Modern UI Assets ---');
    await new Promise((resolve, reject) => {
        http.get(`${baseUrl}/`, (res) => {
            assert.strictEqual(res.statusCode, 200);
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                assert.ok(body.includes('device-container'), 'Phải chứa container xoay');
                assert.ok(body.includes('Family Remote'), 'Phải chứa brand title');
                assert.ok(body.includes('takeScreenshot'), 'Phải có tính năng Screenshot');
                assert.ok(body.includes('open_settings'), 'Phải có nút mở Settings');
                console.log('✔ Test 1: HTTP GET / thành công và đầy đủ cấu trúc UI mới.');
                resolve();
            });
        }).on('error', reject);
    });

    // Test 2: Kết nối Phone và Web
    console.log('\n--- 2. Kiểm tra Kết nối Phone & Web ---');
    const wsWeb = new WebSocket(wsUrl);
    const wsPhone = new WebSocket(wsUrl);

    await Promise.all([
        new Promise(r => wsWeb.on('open', r)),
        new Promise(r => wsPhone.on('open', r))
    ]);

    let webReceivedStatus = null;
    wsWeb.on('message', (msg) => {
        const data = JSON.parse(msg.toString());
        if (data.type === 'status') {
            webReceivedStatus = data;
        }
    });

    wsWeb.send(JSON.stringify({ type: 'register', role: 'web' }));
    wsPhone.send(JSON.stringify({
        type: 'register',
        role: 'phone',
        screenWidth: 1080,
        screenHeight: 2400,
        orientation: 'portrait'
    }));

    await new Promise(r => setTimeout(r, 400));
    assert.ok(webReceivedStatus);
    assert.strictEqual(webReceivedStatus.status, 'phone_connected');
    assert.strictEqual(webReceivedStatus.screenWidth, 1080);
    assert.strictEqual(webReceivedStatus.orientation, 'portrait');
    console.log('✔ Test 2: Đăng ký thành công kèm thông tin kích thước và chiều xoay ban đầu.');

    // Test 3: Bắn sự kiện Auto Screen Rotation (screen_size)
    console.log('\n--- 3. Kiểm tra Tự Động Xoay Màn Hình (screen_size event) ---');
    let rotationEventReceived = null;
    wsWeb.on('message', (msg) => {
        const data = JSON.parse(msg.toString());
        if (data.type === 'screen_size') {
            rotationEventReceived = data;
        }
    });

    // Điện thoại xoay ngang: 2400 x 1080
    wsPhone.send(JSON.stringify({
        type: 'screen_size',
        width: 2400,
        height: 1080,
        orientation: 'landscape'
    }));

    await new Promise(r => setTimeout(r, 300));
    assert.ok(rotationEventReceived);
    assert.strictEqual(rotationEventReceived.width, 2400);
    assert.strictEqual(rotationEventReceived.height, 1080);
    assert.strictEqual(rotationEventReceived.orientation, 'landscape');
    console.log('✔ Test 3: Nhận và chuyển tiếp sự kiện tự động xoay ngang/dọc thành công.');

    // Test 4: Kiểm tra đầy đủ các lệnh mở rộng (Navigation, Hardware, System Settings)
    console.log('\n--- 4. Kiểm tra Toàn Bộ Lệnh Điều Khiển Mới ---');
    const receivedCommands = [];
    wsPhone.on('message', (msg) => {
        const data = JSON.parse(msg.toString());
        receivedCommands.push(data);
    });

    const newActions = [
        'mute', 'open_settings', 'open_wifi', 'open_accessibility', 'open_app_permissions',
        'back', 'home', 'recents', 'notifications', 'power', 'lock', 'volume_up', 'volume_down'
    ];

    for (const action of newActions) {
        wsWeb.send(JSON.stringify({ type: action }));
    }

    await new Promise(r => setTimeout(r, 400));
    assert.strictEqual(receivedCommands.length, newActions.length);
    for (let i = 0; i < newActions.length; i++) {
        assert.strictEqual(receivedCommands[i].type, newActions[i]);
    }
    console.log(`✔ Test 4: Relay thành công ${newActions.length}/${newActions.length} lệnh mở rộng.`);

    // Test 5: Cleanup & Ngắt kết nối
    console.log('\n--- 5. Kiểm tra Ngắt Kết Nối & Dọn Dẹp ---');
    let phoneDisconnected = false;
    wsWeb.on('message', (msg) => {
        const data = JSON.parse(msg.toString());
        if (data.type === 'status' && data.status === 'phone_disconnected') {
            phoneDisconnected = true;
        }
    });

    wsPhone.close();
    await new Promise(r => setTimeout(r, 400));
    assert.ok(phoneDisconnected);
    console.log('✔ Test 5: Dọn dẹp thành công sau khi Phone ngắt kết nối.');

    wsWeb.close();
    server.close();

    console.log('\n🎉 TẤT CẢ CÁC BÀI TEST ROTATION & MODERN UI ĐỀU THÀNH CÔNG 100%!');
    process.exit(0);
}

runTestSuite().catch(err => {
    console.error('Test thất bại:', err);
    process.exit(1);
});
