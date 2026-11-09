package com.family.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class RemoteInputAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RemoteInputService"
        var instance: RemoteInputAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility Service đã kết nối thành công!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Không cần bắt sự kiện của app khác, chỉ cần sẵn sàng nhận lệnh điều khiển
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service bị gián đoạn")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Accessibility Service đã dừng")
    }

    /**
     * Mô phỏng chạm (Tap/Click) tại vị trí (x, y)
     */
    fun performTap(x: Float, y: Float, durationMs: Long = 50) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Chạm thành công tại ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "Chạm bị hủy tại ($x, $y)")
            }
        }, null)
    }

    /**
     * Mô phỏng vuốt (Swipe) từ (startX, startY) đến (endX, endY)
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Vuốt thành công từ ($startX, $startY) -> ($endX, $endY)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "Vuốt bị hủy")
            }
        }, null)
    }

    /**
     * Thao tác phím hệ thống: Back, Home, Recent Apps, Nguồn (Power/Khóa màn hình), Thanh thông báo, Âm lượng
     */
    fun performActionBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performActionHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performActionRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun performActionNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun performActionPowerDialog() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }

    fun performActionLockScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        }
    }
}
