package com.family.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private var isSharing by mutableStateOf(false)
    private var isAccessibilityEnabled by mutableStateOf(false)
    private var serverUrlState by mutableStateOf("ws://192.168.1.100:8080")

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_SERVER_URL, serverUrlState)
            }
            startForegroundService(serviceIntent)
            isSharing = true
        } else {
            isSharing = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tự động load server IP lưu trong SharedPreferences nếu có
        val prefs = getSharedPreferences("family_remote", Context.MODE_PRIVATE)
        serverUrlState = prefs.getString("server_url", "ws://192.168.1.100:8080") ?: "ws://192.168.1.100:8080"

        setContent {
            ParentScreen(
                isSharing = isSharing,
                isAccessibilityEnabled = isAccessibilityEnabled,
                serverUrl = serverUrlState,
                onServerUrlChange = { newUrl ->
                    serverUrlState = newUrl
                    prefs.edit().putString("server_url", newUrl).apply()
                },
                onToggleShare = {
                    if (isSharing) {
                        stopSharing()
                    } else {
                        startSharing()
                    }
                },
                onOpenAccessibilitySettings = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isAccessibilityEnabled = RemoteInputAccessibilityService.isRunning()
        isSharing = ScreenCaptureService.isRunning
    }

    private fun startSharing() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopSharing() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(intent)
        isSharing = false
    }
}

@Composable
fun ParentScreen(
    isSharing: Boolean,
    isAccessibilityEnabled: Boolean,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onToggleShare: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    var showConfig by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF6F8FA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(
                    text = "HỖ TRỢ TỪ XA",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isSharing) "ĐANG KẾT NỐI VỚI CON..." else "Khi cần con hỗ trợ, bấm nút tròn bên dưới",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = if (isSharing) Color(0xFF16A34A) else Color(0xFF6B7280)
                )
            }

            // Nút bấm khổng lồ cho người lớn tuổi
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                Button(
                    onClick = onToggleShare,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSharing) Color(0xFFDC2626) else Color(0xFF2563EB)
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isSharing) "TẮT\nHỖ TRỢ" else "BẬT\nHỖ TRỢ",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Phần trạng thái & Hướng dẫn trợ năng
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isAccessibilityEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "⚠️ Cần bật quyền hỗ trợ thao tác",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onOpenAccessibilitySettings,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Bật quyền trong Cài đặt")
                            }
                        }
                    }
                }

                // Nút cấu hình kín đáo dành cho bạn khi setup ban đầu
                TextButton(onClick = { showConfig = !showConfig }) {
                    Text(
                        text = if (showConfig) "Đóng cài đặt máy chủ" else "Cài đặt máy chủ (Dành cho con)",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                }

                if (showConfig) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        label = { Text("Địa chỉ Server WebSocket (VD: ws://IP:8080)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}
