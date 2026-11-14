const assert = require('assert');
const http = require('http');
const WebSocket = require('ws');

async function runTestSuite() {
    console.log('=== BẮT ĐẦU TEST TOÀN DIỆN WEB SERVER & RELAY ===');
    
    // Gán PORT ngẫu nhiên cho test tránh conflict
    const TEST_PORT = 9091;
    process.env.PORT = TEST_PORT;

    const { server, wss } = require('./server.js');
    const baseUrl = `http://127.0.0.1:${TEST_PORT}`;
    const wsUrl = `ws://127.0.0.1:${TEST_PORT}`;

    // Đợi server listen xong
    await new Promise(r => setTimeout(r, 600));

    // Test 1: Kiểm tra HTTP GET trang chủ
    console.log('\n--- 1. Kiểm tra HTTP GET / và /index.html ---');
    await new Promise((resolve, reject) => {
        http.get(`${baseUrl}/`, (res) => {
            assert.strictEqual(res.statusCode, 200);
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                assert.ok(body.includes('phone-canvas'), 'Phải chứa canvas render');
                assert.ok(body.includes('Hỗ Trợ Bố Mẹ'), 'Phải chứa title header');
                console.log('✔ Test 1.1: HTTP GET / trả về 200 và chứa Canvas render thành công.');
                resolve();
            });
        }).on('error', reject);
    });

    await new Promise((resolve, reject) => {
        http.get(`${baseUrl}/index.html`, (res) => {
            assert.strictEqual(res.statusCode, 200);
            console.log('✔ Test 1.2: HTTP GET /index.html thành công.');
            resolve();
        }).on('error', reject);
    });

    // Test 2: Kết nối WebSocket Web Client và nhận status offline khi chưa có phone
    console.log('\n--- 2. Kiểm tra Web Client kết nối khi chưa có Phone ---');
    const wsWeb = new WebSocket(wsUrl);
    await new Promise(r => wsWeb.on('open', r));

    let initialStatus = null;
    wsWeb.on('message', (msg) => {
        const data = JSON.parse(msg.toString());
        if (data.type === 'status') {
            initialStatus = data.status;
        }
    });

    wsWeb.send(JSON.stringify({ type: 'register', role: 'web' }));
    await new Promise(r => setTimeout(r, 300));
    assert.strictEqual(initialStatus, 'phone_disconnected', 'Web phải nhận status phone_disconnected');
    console.log('✔ Test 2: Web nhận đúng thông báo chưa có điện thoại kết nối.');

    // Test 3: Phone kết nối và đăng ký -> Web nhận thông báo phone_connected kèm kích thước màn hình
    console.log('\n--- 3. Kiểm tra Phone kết nối và phát thông báo ---');
    let phoneConnectedNotified = false;
    let receivedWidth = 0;
    let receivedHeight = 0;

    wsWeb.on('message', (msg) => {
        const data = JSON.parse(msg.toString());
        if (data.type === 'status' && data.status === 'phone_connected') {
            phoneConnectedNotified = true;
            receivedWidth = data.screenWidth;
            receivedHeight = data.screenHeight;
        }
    });

    const wsPhone = new WebSocket(wsUrl);
    await new Promise(r => wsPhone.on('open', r));
    wsPhone.send(JSON.stringify({
        type: 'register',
        role: 'phone',
        screenWidth: 1080,
        screenHeight: 2400
    }));

    await new Promise(r => setTimeout(r, 400));
    assert.ok(phoneConnectedNotified, 'Web phải nhận được thông báo phone_connected');
    assert.strictEqual(receivedWidth, 1080);
    assert.strictEqual(receivedHeight, 2400);
    console.log('✔ Test 3: Phone kết nối thành công, Web nhận thông báo và độ phân giải chính xác.');

    // Test 4: Truyền frame hình ảnh từ Phone -> Web
    console.log('\n--- 4. Kiểm tra Stream Frame từ Phone sang Web ---');
    let receivedFrame = null;
    wsWeb.on('message', (msg) => {
        const data = JSON.parse(msg.toString());
        if (data.type === 'frame') {
            receivedFrame = data;
        }
    });

    wsPhone.send(JSON.stringify({
        type: 'frame',
        image: 'fake_base64_jpeg_content',
        width: 540,
        height: 1200
    }));

    await new Promise(r => setTimeout(r, 300));
    assert.ok(receivedFrame, 'Web phải nhận được frame');
    assert.strictEqual(receivedFrame.image, 'fake_base64_jpeg_content');
    assert.strictEqual(receivedFrame.width, 540);
    assert.strictEqual(receivedFrame.height, 1200);
    console.log('✔ Test 4: Chuyển tiếp frame hình ảnh nhanh chóng và toàn vẹn.');

    // Test 5: Gửi các lệnh điều khiển từ Web -> Phone
    console.log('\n--- 5. Kiểm tra Gửi Lệnh Điều Khiển từ Web sang Phone ---');
    const receivedCommands = [];
    wsPhone.on('message', (msg) => {
        const data = JSON.parse(msg.toString());
        receivedCommands.push(data);
    });

    // 5.1 Tap
    wsWeb.send(JSON.stringify({ type: 'tap', x: 0.25, y: 0.75 }));
    // 5.2 Swipe
    wsWeb.send(JSON.stringify({ type: 'swipe', startX: 0.1, startY: 0.8, endX: 0.1, endY: 0.2, duration: 300 }));
    // 5.3 Phím hệ thống
    const systemActions = ['back', 'home', 'recents', 'notifications', 'power', 'lock', 'volume_up', 'volume_down'];
    for (const act of systemActions) {
        wsWeb.send(JSON.stringify({ type: act }));
    }

    await new Promise(r => setTimeout(r, 400));
    assert.strictEqual(receivedCommands.length, 2 + systemActions.length);
    assert.strictEqual(receivedCommands[0].type, 'tap');
    assert.strictEqual(receivedCommands[0].x, 0.25);
    assert.strictEqual(receivedCommands[1].type, 'swipe');
    assert.strictEqual(receivedCommands[1].endY, 0.2);
    console.log(`✔ Test 5: Tất cả ${receivedCommands.length} lệnh (tap, swipe, navigation, hardware keys) được relay thành công.`);

    // Test 6: Kiểm tra ngắt kết nối và Edge cases (Phone disconnect, Web dọn dẹp)
    console.log('\n--- 6. Kiểm tra Ngắt Kết Nối & Dọn Dẹp Tài Nguyên ---');
    let phoneDisconnectedNotified = false;
    wsWeb.on('message', (msg) => {
        const data = JSON.parse(msg.toString());
        if (data.type === 'status' && data.status === 'phone_disconnected') {
            phoneDisconnectedNotified = true;
        }
    });

    wsPhone.close();
    await new Promise(r => setTimeout(r, 400));
    assert.ok(phoneDisconnectedNotified, 'Web phải nhận thông báo phone_disconnected khi phone ngắt kết nối');
    console.log('✔ Test 6: Dọn dẹp phoneClient hoàn tất khi ngắt kết nối đột ngột.');

    // Cleanup
    wsWeb.close();
    server.close();

    console.log('\n🎉 TẤT CẢ CÁC BÀI TEST SERVER & GIAO DIỆN ĐỀU THÀNH CÔNG 100%!');
    process.exit(0);
}

runTestSuite().catch(err => {
    console.error('Test thất bại:', err);
    process.exit(1);
});
