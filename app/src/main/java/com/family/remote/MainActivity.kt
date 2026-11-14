package com.family.remote

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var isSharing by mutableStateOf(false)
    private var isAccessibilityEnabled by mutableStateOf(false)
    private var serverUrlState by mutableStateOf("wss://relaxed-major-hen.ngrok-free.app")

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_SERVER_URL, serverUrlState.trim())
            }
            startForegroundService(serviceIntent)
            isSharing = true
        } else {
            isSharing = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkAndPromptAccessibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("family_remote", Context.MODE_PRIVATE)
        serverUrlState = prefs.getString("server_url", "wss://relaxed-major-hen.ngrok-free.app") ?: "wss://relaxed-major-hen.ngrok-free.app"

        // Vừa vào app -> Tự động xin quyền thông báo (Android 13+) và dẫn vào mở quyền Hỗ trợ tiếp cận
        requestPermissionsOnStartup()

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
                        if (!isAccessibilityEnabled) {
                            openAccessibilitySettings()
                        } else {
                            startSharing()
                        }
                    }
                },
                onOpenAccessibilitySettings = { openAccessibilitySettings() },
                onOpenAppDetails = { openAppDetailsSettings() },
                onDisableAccessibilityForBanking = { disableAccessibilityForBanking() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isAccessibilityEnabled = RemoteInputAccessibilityService.isRunning()
        isSharing = ScreenCaptureService.isRunning
    }

    private fun requestPermissionsOnStartup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkAndPromptAccessibility()
    }

    private fun checkAndPromptAccessibility() {
        if (!RemoteInputAccessibilityService.isRunning()) {
            openAccessibilitySettings()
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun startSharing() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
            projectionManager.createScreenCaptureIntent(config)
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun stopSharing() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(intent)
        isSharing = false
        // Tự động tắt quyền trợ năng khi bấm tắt chia sẻ để bảo vệ ứng dụng ngân hàng
        RemoteInputAccessibilityService.instance?.disableService()
        isAccessibilityEnabled = false
    }

    private fun disableAccessibilityForBanking() {
        RemoteInputAccessibilityService.instance?.disableService()
        isAccessibilityEnabled = false
        Toast.makeText(this, "Đã tắt quyền trợ năng để sử dụng app ngân hàng an toàn!", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ParentScreen(
    isSharing: Boolean,
    isAccessibilityEnabled: Boolean,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onToggleShare: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onDisableAccessibilityForBanking: () -> Unit
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

            // Phần trạng thái & Cảnh báo mở khóa nếu máy cấm
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isAccessibilityEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "⚠️ Cần cấp quyền hỗ trợ thao tác",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Bấm nút dưới để vào bật 'Hỗ Trợ Gia Đình'.\n2. Nếu bị mờ/cấm: Bấm 'Mở khóa hạn chế' -> Bấm dấu 3 chấm góc trên -> Cho phép cài đặt bị hạn chế.",
                                fontSize = 13.sp,
                                color = Color(0xFF78350F),
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onOpenAccessibilitySettings,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Bật quyền")
                                }
                                OutlinedButton(
                                    onClick = onOpenAppDetails,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mở khóa hạn chế", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else if (isAccessibilityEnabled && !isSharing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🏦 Bảo vệ ứng dụng ngân hàng",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1E40AF)
                                )
                                Text(
                                    text = "Tắt quyền trợ năng để đăng nhập app ngân hàng an toàn.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF3B82F6)
                                )
                            }
                            Button(
                                onClick = onDisableAccessibilityForBanking,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("Tắt ngay", fontSize = 12.sp)
                            }
                        }
                    }
                }

                TextButton(onClick = { showConfig = !showConfig }) {
                    Text(
                        text = if (showConfig) "Đóng cài đặt máy chủ" else "Cài đặt máy chủ (Dành cho con)",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                }

                if (showConfig) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        label = { Text("Địa chỉ WebSocket (ws://... hoặc wss://...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                                    if (text.startsWith("ws://") || text.startsWith("wss://")) {
                                        onServerUrlChange(text)
                                        Toast.makeText(context, "Đã dán link từ bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Nếu copy link https://...trycloudflare.com -> tự động chuyển sang wss://
                                        val wsConverted = if (text.startsWith("https://")) {
                                            text.replaceFirst("https://", "wss://")
                                        } else if (text.startsWith("http://")) {
                                            text.replaceFirst("http://", "ws://")
                                        } else {
                                            text
                                        }
                                        onServerUrlChange(wsConverted)
                                        Toast.makeText(context, "Đã nhận link: $wsConverted", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Bộ nhớ tạm trống!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📋 Dán link", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onServerUrlChange("wss://relaxed-major-hen.ngrok-free.app")
                                Toast.makeText(context, "Đã đặt về mặc định", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("↺ Mặc định", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
