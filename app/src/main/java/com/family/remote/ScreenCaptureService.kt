package com.family.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ScreenCaptureService : Service() {

    companion object {
        const val TAG = "ScreenCaptureService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_SERVER_URL = "EXTRA_SERVER_URL"

        var isRunning = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: "ws://192.168.1.100:8080"

                if (resultData != null) {
                    startForegroundNotification()
                    initScreenMetrics()
                    connectWebSocket(serverUrl, resultCode, resultData)
                    isRunning = true
                }
            }
            ACTION_STOP -> {
                stopScreenCapture()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "family_remote_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Dịch vụ hỗ trợ màn hình",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Đang kết nối với con")
            .setContentText("Màn hình đang được chia sẻ an toàn...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(101, notification)
    }

    private fun initScreenMetrics() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenDensity = metrics.densityDpi
        // Giảm kích thước capture để tối ưu băng thông và giảm độ trễ
        val (w, h) = RemoteCommandParser.calculateCaptureResolution(metrics.widthPixels, metrics.heightPixels, 0.5f)
        screenWidth = w
        screenHeight = h
        Log.i(TAG, "Screen resolution: ${screenWidth}x${screenHeight}, density: $screenDensity")
    }

    private fun connectWebSocket(serverUrl: String, resultCode: Int, resultData: Intent) {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket kết nối thành công tới server!")
                // Đăng ký vai trò là client điện thoại
                val registerMsg = JSONObject().apply {
                    put("type", "register")
                    put("role", "phone")
                    put("screenWidth", screenWidth)
                    put("screenHeight", screenHeight)
                }
                webSocket.send(registerMsg.toString())

                // Bắt đầu capture màn hình
                startMediaProjection(resultCode, resultData)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleRemoteCommand(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Lỗi WebSocket: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket đã đóng: $reason")
            }
        })
    }

    private fun startMediaProjection(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        var lastFrameTime = 0L
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            // Giới hạn khoảng 15-20 FPS để mạng mượt
            if (now - lastFrameTime < 60) {
                val img = reader.acquireLatestImage()
                img?.close()
                return@setOnImageAvailableListener
            }
            lastFrameTime = now

            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    processAndSendImage(image)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi đọc frame: ${e.message}")
            } finally {
                image?.close()
            }
        }, null)
    }

    private fun processAndSendImage(image: Image) {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Cắt ảnh đúng kích thước nếu có padding
        val croppedBitmap = if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } else {
            bitmap
        }

        // Nén ảnh sang JPEG để truyền nhẹ
        val outputStream = ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val jpegBytes = outputStream.toByteArray()

        val base64Data = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val frameJson = JSONObject().apply {
            put("type", "frame")
            put("image", base64Data)
            put("width", screenWidth)
            put("height", screenHeight)
        }

        webSocket?.send(frameJson.toString())

        if (croppedBitmap != bitmap) {
            croppedBitmap.recycle()
        }
        bitmap.recycle()
    }

    /**
     * Nhận lệnh từ laptop của con gửi về để thực hiện cử chỉ
     */
    private fun handleRemoteCommand(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val type = json.optString("type")
            val accessibility = RemoteInputAccessibilityService.instance

            if (accessibility == null) {
                Log.w(TAG, "Chưa bật Accessibility Service!")
                return
            }

            // Quy đổi tỉ lệ từ Web về độ phân giải thực của máy
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val realWidth = metrics.widthPixels.toFloat()
            val realHeight = metrics.heightPixels.toFloat()

            when (type) {
                "tap" -> {
                    val normX = json.getDouble("x")
                    val normY = json.getDouble("y")
                    val (tapX, tapY) = RemoteCommandParser.parseNormalizedTap(normX, normY, realWidth, realHeight)
                    accessibility.performTap(tapX, tapY)
                }
                "swipe" -> {
                    val normStartX = json.getDouble("startX")
                    val normStartY = json.getDouble("startY")
                    val normEndX = json.getDouble("endX")
                    val normEndY = json.getDouble("endY")
                    val (start, end) = RemoteCommandParser.parseNormalizedSwipe(normStartX, normStartY, normEndX, normEndY, realWidth, realHeight)
                    val duration = json.optLong("duration", 300)
                    accessibility.performSwipe(start.first, start.second, end.first, end.second, duration)
                }
                "back" -> accessibility.performActionBack()
                "home" -> accessibility.performActionHome()
                "recents" -> accessibility.performActionRecents()
                "notifications" -> accessibility.performActionNotifications()
                "power" -> accessibility.performActionPowerDialog()
                "lock" -> accessibility.performActionLockScreen()
                "volume_up" -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
                "volume_down" -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                "disconnect" -> {
                    // Khi con bấm Tắt phiên trên máy tính, app trên máy bố mẹ tự tắt kết nối và dừng dịch vụ
                    stopScreenCapture()
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi thực thi lệnh: ${e.message}")
        }
    }

    private fun stopScreenCapture() {
        isRunning = false
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            webSocket?.close(1000, "User stopped")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi dừng capture: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        serviceScope.cancel()
    }
}
